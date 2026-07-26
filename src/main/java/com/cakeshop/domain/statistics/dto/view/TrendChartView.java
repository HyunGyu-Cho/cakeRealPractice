package com.cakeshop.domain.statistics.dto.view;

import java.util.List;

/**
 * 인라인 SVG 꺾은선 차트에 필요한 값 묶음. 좌표계는 viewBox "0 0 100 100"이고
 * y는 위가 0이라 값이 클수록 작아진다. 템플릿은 문자열을 그대로 꽂기만 한다.
 */
public record TrendChartView(
    List<TrendPointView> points,
    String orderPolyline,
    String salesPolyline,
    long maxOrderCount,
    long maxNetSalesAmount
) {
    public static TrendChartView empty() {
        return new TrendChartView(List.of(), "", "", 0, 0);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
