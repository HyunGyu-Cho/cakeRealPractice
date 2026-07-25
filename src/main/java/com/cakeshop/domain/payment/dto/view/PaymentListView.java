package com.cakeshop.domain.payment.dto.view;

import java.time.LocalDateTime;

public record PaymentListView(
    Long id,
    Long orderId,
    String paymentKey,
    String orderNumber,
    Long memberId,
    String memberName,
    String method,
    long amount,
    String status,
    LocalDateTime approvedAt,
    LocalDateTime canceledAt,
    String cancellationStatus,
    String cancellationReason
) {
}
