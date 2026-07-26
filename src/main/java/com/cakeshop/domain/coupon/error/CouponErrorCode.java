package com.cakeshop.domain.coupon.error;

import com.cakeshop.global.error.ErrorCode;

public enum CouponErrorCode implements ErrorCode {

    EXPIRED_COUPON("COUPON_001", "만료된 쿠폰입니다.", 400),
    NOT_FOUND("COUPON_002", "쿠폰을 찾을 수 없습니다.", 404),
    NOT_OWNED("COUPON_003", "보유하지 않은 쿠폰입니다.", 403),
    ALREADY_USED("COUPON_004", "이미 사용한 쿠폰입니다.", 400),
    MINIMUM_ORDER_AMOUNT("COUPON_005", "최소 주문 금액을 채우지 못했습니다.", 400),
    NOT_ISSUABLE("COUPON_006", "지금은 발급할 수 없는 쿠폰입니다.", 400),
    SOLD_OUT("COUPON_007", "쿠폰이 모두 소진되었습니다.", 400),
    ALREADY_DOWNLOADED("COUPON_008", "이미 받은 쿠폰입니다.", 400),
    INVALID_STATUS_TRANSITION("COUPON_009", "변경할 수 없는 쿠폰 상태입니다.", 400),
    USE_FAILED("COUPON_010", "쿠폰을 사용할 수 없습니다.", 400);

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
