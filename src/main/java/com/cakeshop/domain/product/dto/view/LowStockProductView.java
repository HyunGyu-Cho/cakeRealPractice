package com.cakeshop.domain.product.dto.view;

/**
 * 재고가 임계값 이하로 떨어진 판매 중 상품. 재고 부족은 stock_quantity의 파생값이므로
 * 별도 상태로 저장하지 않는다. 재고를 관리하지 않는 상품(NULL)은 대상이 아니다.
 */
public record LowStockProductView(
    Long productId,
    String productName,
    int stockQuantity
) {
}
