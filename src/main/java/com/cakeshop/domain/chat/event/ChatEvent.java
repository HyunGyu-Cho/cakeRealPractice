package com.cakeshop.domain.chat.event;

import com.cakeshop.domain.chat.dto.view.ChatMessageView;
import java.time.LocalDateTime;

public record ChatEvent(
    ChatEventType type,
    Long roomId,
    Long customerId,
    String customerUsername,
    ChatMessageView message,
    Long lastReadMessageId,
    String roomStatus,
    LocalDateTime occurredAt
) {
    public static ChatEvent message(Long roomId, Long customerId, String username,
                                    ChatMessageView message) {
        return new ChatEvent(ChatEventType.MESSAGE_CREATED, roomId, customerId, username,
            message, null, null, LocalDateTime.now());
    }

    public static ChatEvent read(Long roomId, Long customerId, String username, Long lastId) {
        return new ChatEvent(ChatEventType.MESSAGES_READ, roomId, customerId, username,
            null, lastId, null, LocalDateTime.now());
    }

    public static ChatEvent status(Long roomId, Long customerId, String username, String status) {
        return new ChatEvent(ChatEventType.ROOM_STATUS_CHANGED, roomId, customerId, username,
            null, null, status, LocalDateTime.now());
    }

    public static ChatEvent summary(Long roomId, Long customerId, String username) {
        return new ChatEvent(ChatEventType.ROOM_SUMMARY_CHANGED, roomId, customerId, username,
            null, null, null, LocalDateTime.now());
    }
}
