package com.cakeshop.domain.notification.dto.view;

import com.cakeshop.domain.notification.entity.Notification;
import java.time.LocalDateTime;

/** 고객·관리자 알림 목록 한 줄. */
public record NotificationView(
    Long id,
    String type,
    String typeLabel,
    String title,
    String content,
    boolean read,
    String targetUrl,
    LocalDateTime createdAt
) {
    public static NotificationView from(Notification notification) {
        return new NotificationView(
            notification.getId(),
            notification.getNotificationType().name(),
            notification.getNotificationType().label(),
            notification.getTitle(),
            notification.getContent(),
            notification.isRead(),
            notification.getTargetUrl(),
            notification.getCreatedAt()
        );
    }
}
