package com.cakeshop.domain.product.dto.view;

/**
 * [공개 계약] 1차 합의 "ProductQueryService.getSalesInfo(id) → (판매가능여부, 가격, 재고)"의 반환 타입.
 * cart·order가 사용 예정이다 — 시그니처 변경 시 사용처(시은↔수민·주환) 합의 필요.
 * stockQuantity가 null이면 재고 관리를 하지 않는 상품(주문제작)이다.
 */
public record ProductSalesInfo(
    Long productId,
    boolean onSale,
    Long price,
    Integer stockQuantity
) {
}
