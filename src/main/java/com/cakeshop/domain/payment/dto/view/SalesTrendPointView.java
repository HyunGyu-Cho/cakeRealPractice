package com.cakeshop.domain.payment.dto.view;

/** 집계 단위 한 구간의 순매출. label 포맷은 order의 주문 추이와 같아 statistics가 라벨로 합칠 수 있다. */
public record SalesTrendPointView(
    String label,
    long netSalesAmount
) {
}
