package com.cakeshop.domain.order.dto.view;

public record OrderItemView(
    Long id,
    Long productId,
    String productName,
    String productType,
    int quantity,
    long unitPrice,
    long totalAmount,
    int preparationDays,
    int cancellationLimitDays
) {
}
