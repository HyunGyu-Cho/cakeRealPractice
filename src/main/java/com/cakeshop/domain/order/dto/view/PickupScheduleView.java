package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/** 특정 날짜의 픽업 일정 한 건. 취소·반려된 주문은 담기지 않는다. */
public record PickupScheduleView(
    Long orderId,
    String orderNumber,
    String productSummary,
    String status,
    LocalDateTime pickupAt
) {
}
