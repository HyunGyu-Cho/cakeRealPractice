package com.cakeshop.domain.product.dto.view;

/** product_type별 판매 가능 상품 수 집계 한 행. 화면에는 CategorySummaryView로 바꿔 내보낸다. */
public record ProductTypeCountRow(String productType, long productCount) {
}
