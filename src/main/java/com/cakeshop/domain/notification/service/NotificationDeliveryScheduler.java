package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.notification.dto.view.NotificationDeliveryRetryRow;
import com.cakeshop.domain.notification.event.NotificationEvent;
import com.cakeshop.domain.notification.event.NotificationPusher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 즉시 푸시가 실패했거나 유실된 전달 건을 재시도한다.
 * 메서드 전체를 트랜잭션으로 묶지 않는다 — 한 건 실패가 나머지를 되돌리면 안 된다.
 * 단일 인스턴스 전제이며, 다중 인스턴스로 확장할 때는
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}나 ShedLock 같은 잠금이 필요하다.
 */
@Component
public class NotificationDeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final NotificationDeliveryService deliveryService;
    private final NotificationPusher pusher;

    public NotificationDeliveryScheduler(NotificationDeliveryService deliveryService,
                                         NotificationPusher pusher) {
        this.deliveryService = deliveryService;
        this.pusher = pusher;
    }

    @Scheduled(
        fixedDelayString = "${cakeshop.notification.delivery.retry-interval:60000}",
        initialDelayString = "${cakeshop.notification.delivery.retry-initial-delay:60000}")
    public void retryFailedDeliveries() {
        List<NotificationDeliveryRetryRow> targets = deliveryService.findRetryTargets(BATCH_SIZE);
        if (targets.isEmpty()) {
            return;
        }
        log.info("알림 전달 재시도를 시작합니다. 대상={}건", targets.size());
        for (NotificationDeliveryRetryRow target : targets) {
            retryOne(target);
        }
    }

    private void retryOne(NotificationDeliveryRetryRow target) {
        try {
            String recipient = pusher.pushToMember(NotificationEvent.toMember(
                target.receiverId(), target.deliveryId(), target.toNotificationView()));
            deliveryService.markSent(target.deliveryId(), target.status(), recipient);
        } catch (RuntimeException e) {
            log.warn("알림 전달 재시도에 실패했습니다. deliveryId={}, 시도={}회",
                target.deliveryId(), target.retryCount() + 1, e);
            markFailureQuietly(target, e);
        }
    }

    private void markFailureQuietly(NotificationDeliveryRetryRow target, RuntimeException cause) {
        try {
            deliveryService.markRetryFailure(target.deliveryId(), target.status(),
                target.retryCount(), cause.getClass().getSimpleName(), cause.getMessage());
        } catch (RuntimeException e) {
            // 한 건의 기록 실패가 남은 배치를 멈추게 하지 않는다. 다음 주기에 다시 집힌다.
            log.warn("알림 전달 실패 기록에 실패했습니다. deliveryId={}", target.deliveryId(), e);
        }
    }
}
