package com.cakeshop.domain.order.error;

import com.cakeshop.global.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    INVALID_STATUS_TRANSITION("ORDER_001", "허용되지 않는 주문 상태 변경입니다.", 400),
    NOT_FOUND("ORDER_002", "주문을 찾을 수 없습니다.", 404),
    CHECKOUT_NOT_FOUND("ORDER_003", "체크아웃 정보가 만료되었습니다. 장바구니에서 다시 시작해 주세요.", 400),
    CHECKOUT_FORBIDDEN("ORDER_004", "다른 회원의 체크아웃 정보에는 접근할 수 없습니다.", 403),
    INVALID_PICKUP_AT("ORDER_005", "선택할 수 없는 픽업 일시입니다.", 400),
    CANCELLATION_DEADLINE_PASSED("ORDER_006", "주문 취소 가능 시간이 지났습니다.", 400),
    CANCELLATION_NOT_ALLOWED("ORDER_007", "현재 상태에서는 주문을 취소할 수 없습니다.", 400),
    CREATE_FAILED("ORDER_008", "주문을 생성하지 못했습니다.", 500);

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
