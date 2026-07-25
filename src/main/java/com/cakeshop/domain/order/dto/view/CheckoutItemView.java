package com.cakeshop.domain.order.dto.view;

public record CheckoutItemView(
    Long cartItemId,
    Long productId,
    String productName,
    String productType,
    String imageUrl,
    int quantity,
    long unitPrice,
    long totalPrice,
    int preparationDays,
    int cancellationLimitDays
) {
}
