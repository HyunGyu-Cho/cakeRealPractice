package com.cakeshop.domain.product.dto.view;

/**
 * [공개 계약] 홈 카테고리 카드 한 칸. 상품이 0개인 유형도 카드가 사라지지 않게
 * {@code ProductType} 4개를 기준으로 만든다.
 *
 * <p>{@code description}은 저장값이 아니라 집계에서 나온 파생 문구다.
 */
public record CategorySummaryView(
    String productType,
    String productTypeLabel,
    String description,
    long productCount) {
}
