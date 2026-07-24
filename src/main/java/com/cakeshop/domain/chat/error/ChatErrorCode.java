package com.cakeshop.domain.chat.error;

import com.cakeshop.global.error.ErrorCode;

public enum ChatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND("CHAT_001", "채팅방을 찾을 수 없습니다.", 404);

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
