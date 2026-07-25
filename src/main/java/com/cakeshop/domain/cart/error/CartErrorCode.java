package com.cakeshop.domain.cart.error;

import com.cakeshop.global.error.ErrorCode;

public enum CartErrorCode implements ErrorCode {

    PRODUCT_NOT_ON_SALE("CART_001", "판매 중이 아닌 상품입니다.", 400),
    CUSTOM_PRODUCT_NOT_ALLOWED("CART_002", "주문 제작 상품은 장바구니에 담을 수 없습니다.", 400),
    INVALID_QUANTITY("CART_003", "수량은 1개 이상이어야 합니다.", 400),
    STOCK_EXCEEDED("CART_004", "현재 재고보다 많은 수량을 담을 수 없습니다.", 400),
    ITEM_NOT_FOUND("CART_005", "장바구니 항목을 찾을 수 없습니다.", 404),
    EMPTY_SELECTION("CART_006", "상품을 한 개 이상 선택해 주세요.", 400);

    private final String code;
    private final String message;
    private final int status;

    CartErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
