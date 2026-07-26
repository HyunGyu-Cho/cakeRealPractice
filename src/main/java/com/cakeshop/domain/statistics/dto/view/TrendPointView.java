package com.cakeshop.domain.statistics.dto.view;

/**
 * 추이 그래프의 한 점. order(주문 건수·주문 금액)와 payment(순매출)를 라벨로 합친 결과다.
 * SVG 좌표는 템플릿에 계산식을 두지 않기 위해 여기서 0~100으로 정규화해 담는다.
 */
public record TrendPointView(
    String label,
    long orderCount,
    long orderAmount,
    long netSalesAmount,
    double x,
    double orderY,
    double salesY
) {
}
