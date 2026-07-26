package com.cakeshop.domain.order.dto.view;

/** 집계 단위 한 구간의 주문 실적. label은 Mapper가 만든 표시용 문자열이다. */
public record OrderTrendPointView(
    String label,
    long orderCount,
    long orderAmount
) {
}
