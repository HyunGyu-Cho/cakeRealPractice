package com.cakeshop.domain.notification.dto.view;

import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

/**
 * 재시도 대상 한 건. 전달 행과 원본 알림을 함께 읽어 푸시 payload를 다시 만든다.
 * 같은 도메인 테이블(notification_deliveries + notifications) 조인이라 허용된다.
 */
public record NotificationDeliveryRetryRow(
    Long deliveryId,
    DeliveryStatus status,
    int retryCount,
    Long notificationId,
    Long receiverId,
    NotificationType notificationType,
    String title,
    String content,
    boolean read,
    String targetUrl,
    LocalDateTime createdAt
) {
    /** 즉시 푸시와 같은 payload를 재구성한다. */
    public NotificationView toNotificationView() {
        return new NotificationView(
            notificationId,
            notificationType.name(),
            notificationType.label(),
            title,
            content,
            read,
            targetUrl,
            createdAt
        );
    }
}
