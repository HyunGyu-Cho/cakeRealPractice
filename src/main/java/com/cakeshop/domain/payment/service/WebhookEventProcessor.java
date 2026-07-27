package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.entity.WebhookEvent;
import com.cakeshop.domain.payment.entity.WebhookProcessStatus;
import com.cakeshop.domain.payment.infra.TossStatusMapper;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.payment.mapper.WebhookEventMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수신한 웹훅을 결제 상태에 반영한다.
 *
 * <p>승인 결과는 확정 단계가 이미 반영하므로 이 처리의 역할은 <b>밀린 상태 따라잡기</b>다 —
 * 가상계좌 입금, 제공자 화면에서 일어난 취소 등. 우리 상태가 이미 최신이면 {@code SKIPPED}로 남긴다.
 */
@Service
public class WebhookEventProcessor {
    private static final int BATCH_SIZE = 100;

    private final WebhookEventMapper webhookEventMapper;
    private final PaymentMapper paymentMapper;

    public WebhookEventProcessor(WebhookEventMapper webhookEventMapper, PaymentMapper paymentMapper) {
        this.webhookEventMapper = webhookEventMapper;
        this.paymentMapper = paymentMapper;
    }

    /** 미처리 이벤트를 반영한다. 한 건이 실패해도 나머지는 계속 처리한다. */
    public int processPending() {
        List<WebhookEvent> events = webhookEventMapper.findUnprocessed(BATCH_SIZE);
        int processed = 0;
        for (WebhookEvent event : events) {
            try {
                processed += process(event) ? 1 : 0;
            } catch (RuntimeException e) {
                webhookEventMapper.markProcessed(event.getId(),
                    WebhookProcessStatus.FAILED.name(), e.getMessage());
            }
        }
        return processed;
    }

    /** @return 결제 상태를 실제로 바꿨으면 {@code true} */
    @Transactional
    public boolean process(WebhookEvent event) {
        PaymentStatus next = TossStatusMapper.toPaymentStatus(event.getProviderStatus());
        Payment payment = event.getTossOrderId() == null ? null
            : paymentMapper.findByTossOrderId(event.getTossOrderId()).orElse(null);

        // 모르는 상태거나 우리가 모르는 결제다. 실패로 남기면 계속 재처리되므로 건너뛴다.
        if (next == null || payment == null || next.name().equals(payment.getStatus())) {
            webhookEventMapper.markProcessed(event.getId(),
                WebhookProcessStatus.SKIPPED.name(), null);
            return false;
        }

        int updated = paymentMapper.syncStatus(payment.getId(), payment.getStatus(),
            next.name(), event.getProviderStatus());
        webhookEventMapper.markProcessed(event.getId(),
            updated == 1 ? WebhookProcessStatus.PROCESSED.name() : WebhookProcessStatus.SKIPPED.name(),
            null);
        return updated == 1;
    }
}
