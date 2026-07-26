package com.cakeshop.domain.notification.dto.form;

import com.cakeshop.domain.notification.entity.NotificationType;
import lombok.Getter;
import lombok.Setter;

/** 관리자 발송 내역 필터. 알 수 없는 값은 필터 없음으로 취급한다. */
@Getter
@Setter
public class NotificationSearchForm {

    private String type;
    private String read;

    public NotificationType normalizedType() {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return NotificationType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public NotificationType getNormalizedType() {
        return normalizedType();
    }

    /** null이면 전체, true/false면 읽음 여부 필터. */
    public Boolean normalizedRead() {
        if ("READ".equalsIgnoreCase(read)) {
            return Boolean.TRUE;
        }
        if ("UNREAD".equalsIgnoreCase(read)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public Boolean getNormalizedRead() {
        return normalizedRead();
    }
}
