package com.cakeshop.domain.notification.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(NotificationEvent event) {
        try {
            if (event.adminBroadcast()) {
                messagingTemplate.convertAndSend("/topic/admin/notifications", event);
            } else {
                messagingTemplate.convertAndSendToUser(
                    event.receiverUsername(), "/queue/notifications", event);
            }
        } catch (RuntimeException e) {
            // 실시간 전달 실패는 이미 커밋된 알림을 되돌리지 않는다. 클라이언트가 재조회로 복구한다.
            log.warn("알림 실시간 전달에 실패했습니다. type={}, receiverId={}",
                event.notification().type(), event.receiverId(), e);
        }
    }
}
