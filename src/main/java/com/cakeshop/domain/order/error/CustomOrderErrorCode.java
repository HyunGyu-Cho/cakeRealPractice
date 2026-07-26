package com.cakeshop.domain.order.error;

import com.cakeshop.global.error.ErrorCode;

/** 주문제작 전용 오류. 일반 주문 오류는 {@link OrderErrorCode}를 쓴다. */
public enum CustomOrderErrorCode implements ErrorCode {
    NOT_CUSTOM_PRODUCT("CUSTOM_001", "주문제작 상품이 아닙니다.", 400),
    PRODUCT_NOT_ON_SALE("CUSTOM_002", "현재 주문제작 요청을 받지 않는 상품입니다.", 400),
    INVALID_OPTION("CUSTOM_003", "선택할 수 없는 옵션입니다.", 400),
    OPTION_REQUIRED("CUSTOM_004", "필수 옵션을 선택해 주세요.", 400),
    TOO_MANY_IMAGES("CUSTOM_005", "참고 이미지는 최대 3장까지 첨부할 수 있습니다.", 400),
    INVALID_IMAGE("CUSTOM_006", "JPG 또는 PNG 이미지만 첨부할 수 있습니다.", 400),
    IMAGE_TOO_LARGE("CUSTOM_007", "참고 이미지는 장당 5MB까지 첨부할 수 있습니다.", 400),
    REQUEST_NOT_FOUND("CUSTOM_008", "주문제작 요청을 찾을 수 없습니다.", 404),
    NOT_UNDER_REVIEW("CUSTOM_009", "검토 중인 요청에만 할 수 있는 작업입니다.", 400),
    QUOTE_NOT_FOUND("CUSTOM_010", "견적을 찾을 수 없습니다.", 404),
    QUOTE_ALREADY_ACCEPTED("CUSTOM_011", "이미 수락된 견적은 변경할 수 없습니다.", 400),
    QUOTE_NOT_SENT("CUSTOM_012", "수락할 수 있는 견적이 없습니다.", 400),
    LINK_NOT_FOUND("CUSTOM_013", "결제 링크를 찾을 수 없습니다.", 404),
    LINK_NOT_PAYABLE("CUSTOM_014", "사용할 수 없는 결제 링크입니다.", 400),
    LINK_EXPIRED("CUSTOM_015", "결제 링크가 만료되었습니다. 관리자에게 재발급을 요청해 주세요.", 400),
    ALREADY_PAID("CUSTOM_016", "이미 결제가 완료된 요청입니다.", 400),
    CANCEL_NOT_ALLOWED("CUSTOM_017", "견적을 수락한 뒤에는 이 화면에서 취소할 수 없습니다.", 400),
    REJECT_REASON_REQUIRED("CUSTOM_018", "반려 사유를 입력해 주세요.", 400),
    PRODUCIBLE_DATE_PASSED("CUSTOM_019", "제작 가능일은 오늘 이후여야 합니다.", 400),
    PRODUCIBLE_DATE_OUT_OF_WINDOW("CUSTOM_020",
        "제작 가능일이 매장 픽업 예약 가능 기간을 벗어납니다. 기간 안의 날짜로 제시해 주세요.", 400),
    NO_PICKUP_SLOT("CUSTOM_021", "제작 가능일 이후 예약할 수 있는 픽업 시간이 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CustomOrderErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
