package com.cakeshop.domain.member.error;

import com.cakeshop.global.error.ErrorCode;

public enum MemberErrorCode implements ErrorCode {

    DUPLICATE_EMAIL("MEMBER_001", "이미 사용 중인 이메일입니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    MemberErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
