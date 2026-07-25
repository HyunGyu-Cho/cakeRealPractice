package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;
import java.util.List;

public record CheckoutView(
    String checkoutId,
    List<CheckoutItemView> items,
    long totalAmount,
    int maximumPreparationDays,
    LocalDateTime pickupAt
) {
}
