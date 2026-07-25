package com.cakeshop.domain.chat.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessage {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private ChatSenderType senderType;
    private ChatMessageType messageType;
    private String content;
    private String imageKey;
    private String imageOriginalName;
    private String imageContentType;
    private Long imageSize;
    private String actionType;
    private String actionUrl;
    private String clientMessageId;
    private LocalDateTime createdAt;
    private boolean read;
}
