package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productType;
    private Integer quantity;
    private Long basePrice;
    private Long optionAmount;
    private Long totalAmount;
    private String requirements;
    private Integer preparationDays;
    private Integer cancellationLimitDays;
}
