package com.cakeshop.global.error;

public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT("COMMON_001", "입력값이 올바르지 않습니다.", 400),
    NOT_FOUND("COMMON_002", "대상을 찾을 수 없습니다.", 404),
    FORBIDDEN("COMMON_003", "권한이 없습니다.", 403),
    INTERNAL_ERROR("COMMON_004", "일시적인 오류가 발생했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;

    CommonErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override 
    public String code() { 
        return code; 
    }

    @Override 
    public String message() { 
        return message; 
    }

    @Override 
    public int status() { 
        return status; 
    }
}
