package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailView(
    Long id,
    String orderNumber,
    Long memberId,
    String ordererName,
    String ordererPhone,
    String pickupName,
    String pickupPhone,
    long originalAmount,
    long discountAmount,
    long finalAmount,
    String status,
    LocalDateTime pickupAt,
    String requestMessage,
    String cancelReason,
    String canceledBy,
    LocalDateTime cancellationDeadline,
    boolean cancellable,
    LocalDateTime createdAt,
    List<OrderItemView> items
) {
}
