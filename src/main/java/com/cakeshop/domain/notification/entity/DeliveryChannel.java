package com.cakeshop.domain.notification.entity;

/**
 * 알림 전달 경로. status가 아니라 type이므로 전이가 없고 생성 시점에 확정된다.
 * 값 집합은 {@code chk_notification_deliveries_channel} CHECK와 일치해야 한다
 * (동기화는 NotificationTypeSqlSyncTests가 검증한다).
 */
public enum DeliveryChannel {

    WEBSOCKET("실시간 푸시");

    private final String label;

    DeliveryChannel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
