package com.cakeshop.domain.cart.error;

import com.cakeshop.global.error.ErrorCode;

public enum CartErrorCode implements ErrorCode {

    PRODUCT_NOT_ON_SALE("CART_001", "판매 중이 아닌 상품입니다.", 400);

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
