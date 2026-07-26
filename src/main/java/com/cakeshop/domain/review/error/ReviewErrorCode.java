package com.cakeshop.domain.review.error;

import com.cakeshop.global.error.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {

    NOT_PICKED_UP("REVIEW_001", "픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400),
    NOT_FOUND("REVIEW_002", "후기를 찾을 수 없습니다.", 404),
    NOT_OWNER("REVIEW_003", "본인이 작성한 후기만 수정·삭제할 수 있습니다.", 403),
    ALREADY_WRITTEN("REVIEW_004", "이미 후기를 작성한 주문 상품입니다.", 400),
    TOO_MANY_IMAGES("REVIEW_005", "이미지는 최대 3장까지 첨부할 수 있습니다.", 400),
    IMAGE_TOO_LARGE("REVIEW_006", "이미지 한 장은 5MB 이하여야 합니다.", 400),
    INVALID_IMAGE("REVIEW_007", "JPG 또는 PNG 이미지만 첨부할 수 있습니다.", 400),
    HIDDEN_REVIEW("REVIEW_008", "관리자가 숨긴 후기입니다.", 403),
    INVALID_STATUS_TRANSITION("REVIEW_009", "변경할 수 없는 후기 상태입니다.", 400),
    SAVE_FAILED("REVIEW_010", "후기를 저장하지 못했습니다.", 500);

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
