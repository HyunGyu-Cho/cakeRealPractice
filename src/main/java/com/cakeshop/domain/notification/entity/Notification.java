package com.cakeshop.domain.notification.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** notifications 테이블. created_at은 DDL DEFAULT에 위임한다. */
@Getter
@Setter
public class Notification {

    private Long id;
    private Long receiverId;
    private Long orderId;
    private Long chatMessageId;
    private NotificationType notificationType;
    private String title;
    private String content;
    private boolean read;
    private LocalDateTime readAt;
    private String targetUrl;
    private LocalDateTime createdAt;
}
