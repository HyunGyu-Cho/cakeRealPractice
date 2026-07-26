package com.cakeshop.domain.member.error;

import com.cakeshop.global.error.ErrorCode;

public enum MemberErrorCode implements ErrorCode {

    DUPLICATE_EMAIL("MEMBER_001", "이미 사용 중인 이메일입니다.", 400),
    PASSWORD_MISMATCH("MEMBER_002", "현재 비밀번호가 올바르지 않습니다.", 400),
    NOT_FOUND("MEMBER_003", "회원 정보를 찾을 수 없습니다.", 404),
    CANNOT_SUSPEND_ADMIN("MEMBER_004", "관리자 계정은 이용을 제한할 수 없습니다.", 400),
    WITHDRAWN_MEMBER("MEMBER_005", "탈퇴한 회원의 상태는 변경할 수 없습니다.", 400),
    INVALID_STATUS_TRANSITION("MEMBER_006", "현재 상태에서 처리할 수 없는 요청입니다.", 400);

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
