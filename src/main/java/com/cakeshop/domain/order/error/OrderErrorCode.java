package com.cakeshop.domain.order.error;

import com.cakeshop.global.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    INVALID_STATUS_TRANSITION("ORDER_001", "허용되지 않는 주문 상태 변경입니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    OrderErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
