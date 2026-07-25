package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 목록 쿼리(products + 대표 이미지 LEFT JOIN)의 평면 결과. service가 ProductSummaryView로 완성한다. */
@Getter
@Setter
public class ProductSummaryRow {

    private Long id;
    private String name;
    private Long basePrice;
    private String productType;
    private Integer stockQuantity;
    private String status;
    private String imageUrl;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
}
