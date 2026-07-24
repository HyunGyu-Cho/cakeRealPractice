package com.cakeshop.domain.coupon.error;

import com.cakeshop.global.error.ErrorCode;

public enum CouponErrorCode implements ErrorCode {

    EXPIRED_COUPON("COUPON_001", "만료된 쿠폰입니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CouponErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
