package com.cakeshop.domain.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 상태 대조와 웹훅 반영을 주기적으로 돌린다.
 *
 * <p>알림 재시도 배치와 같은 풀을 쓰므로 {@code spring.task.scheduling.pool.size}가 1이면
 * 서로 밀린다. 그래서 풀 크기를 4로 올려 뒀다(application.yml).
 * 단일 인스턴스 전제인 것도 알림 배치와 같다.
 */
@Component
public class PaymentReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentReconciliationService reconciliationService;
    private final WebhookEventProcessor webhookEventProcessor;

    public PaymentReconciliationScheduler(PaymentReconciliationService reconciliationService,
                                          WebhookEventProcessor webhookEventProcessor) {
        this.reconciliationService = reconciliationService;
        this.webhookEventProcessor = webhookEventProcessor;
    }

    @Scheduled(
        fixedDelayString = "${cakeshop.payment.reconcile.interval:300000}",
        initialDelayString = "${cakeshop.payment.reconcile.initial-delay:300000}")
    public void reconcile() {
        // 웹훅을 먼저 반영한다. 조회 API를 부르지 않고도 맞출 수 있는 건은 그쪽에서 끝난다.
        int processed = webhookEventProcessor.processPending();
        int changed = reconciliationService.reconcileStaleReady();
        if (processed > 0 || changed > 0) {
            log.info("결제 대조 완료. 웹훅 반영={}건, 상태 정정={}건", processed, changed);
        }
    }
}
