package com.cakeshop.domain.review.dto.view;

import java.math.BigDecimal;

/**
 * [공개 계약] 상품의 후기 집계. {@code products}의 집계 컬럼과 같은 값이며
 * 화면이 상품 조회와 별개로 물어볼 때 쓴다.
 */
public record ReviewSummaryView(long reviewCount, BigDecimal averageRating) {

    public static ReviewSummaryView empty() {
        return new ReviewSummaryView(0L, BigDecimal.ZERO);
    }
}
