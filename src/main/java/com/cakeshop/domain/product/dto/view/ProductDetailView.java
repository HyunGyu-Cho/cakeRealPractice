package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

/** 상세 화면 출력 전용. onSale = ACTIVE이면서 품절이 아님(구매 동선 활성 여부). */
public record ProductDetailView(
    Long id,
    String name,
    String description,
    Long basePrice,
    String productType,
    String productTypeLabel,
    String status,
    String statusLabel,
    Integer stockQuantity,
    String stockLabel,
    Integer preparationDays,
    Integer cancellationLimitDays,
    String imageUrl,
    BigDecimal averageRating,
    Integer reviewCount,
    boolean onSale
) {
}
