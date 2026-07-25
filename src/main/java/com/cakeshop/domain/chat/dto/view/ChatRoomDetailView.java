package com.cakeshop.domain.chat.dto.view;

import java.util.List;

public record ChatRoomDetailView(
    Long roomId,
    Long customerId,
    String customerName,
    String customerEmail,
    String status,
    String statusLabel,
    List<ChatMessageView> messages
) {
}
