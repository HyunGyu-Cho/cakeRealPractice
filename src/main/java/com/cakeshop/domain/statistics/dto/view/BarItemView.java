package com.cakeshop.domain.statistics.dto.view;

/** 막대 그래프 한 칸. heightPercent는 최댓값 대비 0~100이다. */
public record BarItemView(
    String label,
    long value,
    double heightPercent
) {
}
