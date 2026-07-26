package com.cakeshop.domain.statistics.dto.view;

import java.util.List;

/**
 * 인라인 SVG 꺾은선 차트에 필요한 값 묶음. y는 위가 0이라 값이 클수록 작아진다.
 * 템플릿은 문자열을 그대로 꽂기만 한다.
 *
 * <p>좌표계 폭({@code width})은 구간 수에 맞춰 계산한다 — 폭을 100으로 고정하고 화면 비율에
 * 맞춰 늘리면(preserveAspectRatio="none") x·y 배율이 달라져 점 마커가 납작한 타원이 된다.
 * 폭을 데이터에 맞게 잡아 두면 SVG를 균일 배율로 그릴 수 있어 마커가 원으로 남는다.
 * 높이는 항상 100이므로 {@code width}가 그대로 가로:세로 비율이 된다.
 */
public record TrendChartView(
    List<TrendPointView> points,
    String orderPolyline,
    String salesPolyline,
    long maxOrderCount,
    long maxNetSalesAmount,
    int width
) {
    public static TrendChartView empty() {
        return new TrendChartView(List.of(), "", "", 0, 0, 0);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** 템플릿의 viewBox 속성값. 높이는 100으로 고정한다. */
    public String viewBox() {
        return "0 0 " + width + " 100";
    }
}
