package com.cakeshop.domain.product.error;

import com.cakeshop.global.error.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    NOT_ON_SALE("PRODUCT_001", "판매 중인 상품이 아닙니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ProductErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
