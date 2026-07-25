package com.cakeshop.domain.chat.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRoom {
    private Long id;
    private Long customerId;
    private ChatRoomStatus status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
