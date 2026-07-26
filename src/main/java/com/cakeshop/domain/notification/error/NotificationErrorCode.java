package com.cakeshop.domain.notification.error;

import com.cakeshop.global.error.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {

    NOT_FOUND("NOTIFICATION_001", "알림을 찾을 수 없습니다.", 404),
    FORBIDDEN("NOTIFICATION_002", "본인의 알림만 처리할 수 있습니다.", 403),
    INVALID_RECEIVER("NOTIFICATION_003", "알림 수신자가 올바르지 않습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    NotificationErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
