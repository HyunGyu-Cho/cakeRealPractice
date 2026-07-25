package com.cakeshop.domain.chat.dto.view;

import com.cakeshop.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageView(
    Long id,
    Long roomId,
    String senderType,
    String messageType,
    String content,
    boolean hasImage,
    String imageUrl,
    String actionType,
    String actionUrl,
    UUID clientMessageId,
    LocalDateTime createdAt,
    boolean read
) {
    public static ChatMessageView from(ChatMessage message) {
        boolean hasImage = message.getImageKey() != null;
        return new ChatMessageView(
            message.getId(),
            message.getChatRoomId(),
            message.getSenderType().name(),
            message.getMessageType().name(),
            message.getContent(),
            hasImage,
            hasImage ? "/api/chat/messages/" + message.getId() + "/image" : null,
            message.getActionType(),
            message.getActionUrl(),
            message.getClientMessageId() == null
                ? null : UUID.fromString(message.getClientMessageId()),
            message.getCreatedAt(),
            message.isRead()
        );
    }
}
