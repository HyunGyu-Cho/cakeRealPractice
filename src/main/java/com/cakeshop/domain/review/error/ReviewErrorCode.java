package com.cakeshop.domain.review.error;

import com.cakeshop.global.error.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {

    NOT_PICKED_UP("REVIEW_001", "픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ReviewErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
