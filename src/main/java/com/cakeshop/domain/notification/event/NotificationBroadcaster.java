package com.cakeshop.domain.notification.event;

import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.service.NotificationDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 즉시 푸시. 고객 알림은 결과를 전달 이력에 기록하고,
 * 실패분은 {@link com.cakeshop.domain.notification.service.NotificationDeliveryScheduler}가 재시도한다.
 */
@Component
public class NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);

    private final NotificationPusher pusher;
    private final NotificationDeliveryService deliveryService;

    public NotificationBroadcaster(NotificationPusher pusher,
                                   NotificationDeliveryService deliveryService) {
        this.pusher = pusher;
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(NotificationEvent event) {
        if (event.adminBroadcast()) {
            broadcastToAdmins(event);
            return;
        }
        try {
            String recipient = pusher.pushToMember(event);
            deliveryService.markSent(event.deliveryId(), DeliveryStatus.REQUESTED, recipient);
        } catch (RuntimeException e) {
            // 실시간 전달 실패는 이미 커밋된 알림을 되돌리지 않는다. 실패로 기록하고 스케줄러가 재시도한다.
            log.warn("알림 실시간 전달에 실패했습니다. type={}, receiverId={}",
                event.notification().type(), event.receiverId(), e);
            recordFailure(event, e);
        }
    }

    private void broadcastToAdmins(NotificationEvent event) {
        try {
            pusher.pushToAdminTopic(event);
        } catch (RuntimeException e) {
            // 관리자 토픽은 전달 이력을 남기지 않는다. 관리자는 REST 재조회로 복구한다.
            log.warn("관리자 알림 브로드캐스트에 실패했습니다. type={}",
                event.notification().type(), e);
        }
    }

    private void recordFailure(NotificationEvent event, RuntimeException cause) {
        try {
            deliveryService.markInitialFailure(event.deliveryId(), DeliveryStatus.REQUESTED,
                cause.getClass().getSimpleName(), cause.getMessage());
        } catch (RuntimeException e) {
            // 실패 기록까지 실패하면 행이 REQUESTED로 남아 스케줄러가 집어간다(안전한 실패).
            log.warn("알림 전달 실패 기록에 실패했습니다. deliveryId={}", event.deliveryId(), e);
        }
    }
}
