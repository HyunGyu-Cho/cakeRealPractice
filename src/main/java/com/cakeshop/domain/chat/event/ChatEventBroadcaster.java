package com.cakeshop.domain.chat.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ChatEventBroadcaster.class);
    private final SimpMessagingTemplate messagingTemplate;

    public ChatEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                event.customerUsername(), "/queue/chat-events", event);
            messagingTemplate.convertAndSend("/topic/admin/chat-events", event);
        } catch (RuntimeException e) {
            // 실시간 전달 실패는 이미 커밋된 상담 이력을 되돌리지 않는다.
            log.warn("채팅 실시간 이벤트 전달에 실패했습니다. type={}, roomId={}",
                event.type(), event.roomId(), e);
        }
    }
}
