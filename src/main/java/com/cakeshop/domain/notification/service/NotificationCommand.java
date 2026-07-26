package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.notification.entity.NotificationType;

/**
 * [공개 계약] 업무 도메인(chat·order·payment)이 알림을 발행할 때 넘기는 값.
 * 관리자 팬아웃({@link NotificationService#notifyAdmins})에는 receiverId를 비운 채로 넘긴다.
 */
public record NotificationCommand(
    Long receiverId,
    NotificationType type,
    String title,
    String content,
    String targetUrl,
    Long orderId,
    Long chatMessageId
) {
    /** 주문 관련 알림. 상세 링크는 고객 주문 상세로 고정한다. */
    public static NotificationCommand forOrder(Long receiverId, NotificationType type,
                                               Long orderId, String title, String content) {
        return new NotificationCommand(receiverId, type, title, content,
            "/orders/" + orderId, orderId, null);
    }

    /** 채팅 알림. 수신자에 맞는 채팅 화면으로 보낸다. */
    public static NotificationCommand forChat(Long receiverId, NotificationType type,
                                              Long chatMessageId, String title, String content,
                                              String targetUrl) {
        return new NotificationCommand(receiverId, type, title, content,
            targetUrl, null, chatMessageId);
    }

    /** 전체 관리자 대상. {@link NotificationService#notifyAdmins}에만 넘긴다. */
    public static NotificationCommand toAdmins(NotificationType type, String title,
                                               String content, String targetUrl,
                                               Long orderId, Long chatMessageId) {
        return new NotificationCommand(null, type, title, content,
            targetUrl, orderId, chatMessageId);
    }

    NotificationCommand withReceiver(Long newReceiverId) {
        return new NotificationCommand(newReceiverId, type, title, content,
            targetUrl, orderId, chatMessageId);
    }
}
