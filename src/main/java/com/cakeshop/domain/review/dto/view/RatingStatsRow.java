package com.cakeshop.domain.review.dto.view;

import java.math.BigDecimal;

/**
 * 상품 평점 집계 결과. 후기가 하나도 없으면 {@code averageRating}이 {@code null}로 온다
 * (SQL {@code AVG}는 대상이 없으면 NULL이다) — 서비스가 0으로 낮춰 product에 넘긴다.
 */
public record RatingStatsRow(long reviewCount, BigDecimal averageRating) {
}
