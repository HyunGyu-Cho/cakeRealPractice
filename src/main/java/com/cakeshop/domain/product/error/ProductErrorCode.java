package com.cakeshop.domain.product.error;

import com.cakeshop.global.error.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    NOT_ON_SALE("PRODUCT_001", "판매 중인 상품이 아닙니다.", 400),
    NOT_FOUND("PRODUCT_002", "상품을 찾을 수 없습니다.", 404),
    INVALID_IMAGE("PRODUCT_003", "이미지 파일만 첨부할 수 있습니다.", 400),
    CATEGORY_NOT_FOUND("PRODUCT_004", "상품 분류를 찾을 수 없습니다. 카테고리 시드(V6)를 적용해 주세요.", 500);

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
