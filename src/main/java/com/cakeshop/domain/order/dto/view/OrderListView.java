package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

public record OrderListView(
    Long id,
    String orderNumber,
    Long memberId,
    String memberName,
    String productSummary,
    long finalAmount,
    String status,
    LocalDateTime pickupAt,
    LocalDateTime createdAt
) {
}
