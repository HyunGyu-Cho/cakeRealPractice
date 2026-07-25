package com.cakeshop.domain.chat.error;

import com.cakeshop.global.error.ErrorCode;

public enum ChatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND("CHAT_001", "채팅방을 찾을 수 없습니다.", 404),
    MESSAGE_NOT_FOUND("CHAT_002", "채팅 메시지를 찾을 수 없습니다.", 404),
    FORBIDDEN("CHAT_003", "이 채팅에 접근할 권한이 없습니다.", 403),
    EMPTY_MESSAGE("CHAT_004", "메시지 또는 이미지를 입력해 주세요.", 400),
    CONTENT_TOO_LONG("CHAT_005", "메시지는 2,000자 이하로 입력해 주세요.", 400),
    INVALID_IMAGE_TYPE("CHAT_006", "JPG 또는 PNG 이미지만 첨부할 수 있습니다.", 400),
    IMAGE_TOO_LARGE("CHAT_007", "이미지는 5MB 이하만 첨부할 수 있습니다.", 400),
    ROOM_CLOSED("CHAT_008", "종료된 상담에는 관리자가 메시지를 보낼 수 없습니다.", 409),
    INVALID_CURSOR("CHAT_009", "이 채팅방에 속하지 않은 메시지입니다.", 400),
    IMAGE_NOT_FOUND("CHAT_010", "채팅 이미지를 찾을 수 없습니다.", 404),
    DUPLICATE_CONFLICT("CHAT_011", "메시지 중복 처리 중 충돌이 발생했습니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    ChatErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
