package com.cakeshop.domain.payment.error;

import com.cakeshop.global.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    AMOUNT_MISMATCH("PAYMENT_001", "결제 금액이 일치하지 않습니다.", 400),
    NOT_FOUND("PAYMENT_002", "결제 정보를 찾을 수 없습니다.", 404),
    INVALID_METHOD("PAYMENT_003", "지원하지 않는 결제 수단입니다.", 400),
    PAYMENT_FAILED("PAYMENT_004", "결제를 처리하지 못했습니다.", 500),
    APPROVE_FAILED("PAYMENT_005", "결제 승인에 실패했습니다.", 502),
    CANCEL_FAILED("PAYMENT_006", "결제 취소에 실패했습니다.", 502),
    // 승인은 성사됐지만 주문 확정이 실패해 자동 취소한 경우. 돈이 남지 않는다.
    CONFIRM_FAILED("PAYMENT_007", "결제 처리 중 오류가 발생해 결제가 자동 취소되었습니다.", 500),
    NOT_READY("PAYMENT_008", "결제를 시작할 수 없는 상태입니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    PaymentErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
