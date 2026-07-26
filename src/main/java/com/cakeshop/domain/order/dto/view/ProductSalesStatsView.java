package com.cakeshop.domain.order.dto.view;

/** 상품별 주문 실적. 취소·반려 주문은 제외한 집계다. */
public record ProductSalesStatsView(
    Long productId,
    String productName,
    long orderCount,
    long quantity,
    long orderAmount
) {
}
