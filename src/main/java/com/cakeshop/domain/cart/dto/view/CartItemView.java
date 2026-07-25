package com.cakeshop.domain.cart.dto.view;

public record CartItemView(
    Long cartItemId,
    Long productId,
    String productName,
    String imageUrl,
    String productType,
    String productTypeLabel,
    int quantity,
    long currentUnitPrice,
    long currentTotalPrice,
    Integer stockQuantity,
    boolean selectable,
    String unavailableReason
) {
}
