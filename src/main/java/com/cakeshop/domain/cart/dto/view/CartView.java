package com.cakeshop.domain.cart.dto.view;

import java.util.List;

public record CartView(
    List<CartItemView> items,
    int totalQuantity,
    long totalAmount
) {

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
