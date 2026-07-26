package com.cakeshop.domain.store.error;

import com.cakeshop.global.error.ErrorCode;

public enum StoreErrorCode implements ErrorCode {
    NOT_FOUND("STORE_001", "매장 정보를 찾을 수 없습니다.", 404),
    UPDATE_FAILED("STORE_002", "매장 정보를 저장하지 못했습니다.", 500),
    HOLIDAY_ALREADY_EXISTS("STORE_003", "이미 등록된 특정 휴무일입니다.", 409),
    HOLIDAY_NOT_FOUND("STORE_004", "삭제할 특정 휴무일을 찾을 수 없습니다.", 404),
    INVALID_IMAGE("STORE_005", "이미지 파일만 첨부할 수 있습니다.", 400),
    INVALID_PICKUP_AT("STORE_006", "선택할 수 없는 픽업 일시입니다.", 400),
    PICKUP_DATE_CLOSED("STORE_007", "휴무일에는 픽업할 수 없습니다. 다른 날짜를 선택해 주세요.", 400),
    PICKUP_SLOT_TAKEN("STORE_008", "이미 예약된 픽업 시간입니다. 다른 시간을 선택해 주세요.", 409),
    HOLIDAY_HAS_PICKUP("STORE_009", "이 날짜에 픽업 예정 주문이 있어 휴무일로 지정할 수 없습니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    StoreErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
