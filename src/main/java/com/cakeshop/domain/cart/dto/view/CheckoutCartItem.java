package com.cakeshop.domain.cart.dto.view;

/** order 도메인에 전달하는 장바구니 공개 계약. 가격은 호출 시점의 최신 판매가다. */
public record CheckoutCartItem(
    Long cartItemId,
    Long productId,
    int quantity,
    long currentUnitPrice
) {
}
