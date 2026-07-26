package com.cakeshop.domain.review.dto.view;

import java.math.BigDecimal;

/** 기간 후기 요약. 숨김(HIDDEN) 후기는 평점 평균에서 빠진다. */
public record ReviewStatsView(
    long reviewCount,
    BigDecimal averageRating
) {
    public static ReviewStatsView empty() {
        return new ReviewStatsView(0, BigDecimal.ZERO);
    }
}
