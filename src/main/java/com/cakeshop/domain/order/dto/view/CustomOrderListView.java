package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/** 관리자 주문제작 목록 한 행. */
public record CustomOrderListView(
    Long orderId,
    String orderNumber,
    Long memberId,
    String memberName,
    String productName,
    LocalDateTime pickupAt,
    Long desiredBudget,
    Long quotedAmount,
    String status,
    String statusLabel,
    String progressLabel,
    LocalDateTime createdAt
) {
}
