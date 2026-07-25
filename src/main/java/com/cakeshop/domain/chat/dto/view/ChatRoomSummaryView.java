package com.cakeshop.domain.chat.dto.view;

import java.time.LocalDateTime;

public record ChatRoomSummaryView(
    Long roomId,
    Long customerId,
    String customerName,
    String customerEmail,
    String status,
    String statusLabel,
    boolean unanswered,
    int unreadCount,
    Long lastMessageId,
    String lastMessagePreview,
    LocalDateTime lastMessageAt
) {
}
