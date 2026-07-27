package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.entity.WebhookEvent;
import com.cakeshop.domain.payment.entity.WebhookProcessStatus;
import com.cakeshop.domain.payment.mapper.WebhookEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹훅 수신함. 저장만 하고 즉시 응답하기 위해 반영({@link WebhookEventProcessor})과 분리했다 —
 * 제공자는 10초 안에 200을 받지 못하면 재전송하므로, 수신 경로에 결제 처리를 얹으면 안 된다.
 */
@Service
public class WebhookEventService {
    private final WebhookEventMapper webhookEventMapper;

    public WebhookEventService(WebhookEventMapper webhookEventMapper) {
        this.webhookEventMapper = webhookEventMapper;
    }

    /**
     * 이벤트를 받아 적는다.
     *
     * @return 새로 저장했으면 {@code true}, 이미 받은 전송 ID면 {@code false}(재전송)
     */
    @Transactional
    public boolean receive(String eventId, String eventType, String paymentKey, String tossOrderId,
                           String providerStatus, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setPaymentKey(paymentKey);
        event.setTossOrderId(tossOrderId);
        event.setProviderStatus(providerStatus);
        event.setPayload(payload);
        event.setProcessStatus(WebhookProcessStatus.RECEIVED.name());
        try {
            return webhookEventMapper.insertEvent(event) == 1;
        } catch (DuplicateKeyException duplicate) {
            // 전송 ID UNIQUE가 재전송을 걸렀다. 정상 흐름이므로 예외로 올리지 않는다.
            return false;
        }
    }
}
