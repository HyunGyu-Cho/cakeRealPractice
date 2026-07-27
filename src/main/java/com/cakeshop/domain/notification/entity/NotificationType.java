package com.cakeshop.domain.notification.entity;

/**
 * 알림 종류. status가 아니라 type이므로 전이가 없고 생성 시점에 확정된다.
 * 저장값은 영문 enum 이름이며 한글 라벨은 화면에서만 사용한다.
 * {@code ADMIN_} 접두어가 붙은 값의 수신자는 관리자 회원이다.
 */
public enum NotificationType {

    CHAT_MESSAGE("새 메시지"),
    ORDER_PAID("결제 완료"),
    ORDER_IN_PRODUCTION("제작 시작"),
    ORDER_READY_FOR_PICKUP("픽업 준비"),
    ORDER_PICKED_UP("픽업 완료"),
    ORDER_CANCELED("취소·환불 완료"),
    ORDER_REJECTED("주문 거절"),
    CUSTOM_ORDER_QUOTE("견적 도착"),
    PAYMENT_REQUESTED("결제 요청"),
    REVIEW_REPLY("후기 답글"),
    ADMIN_CHAT_MESSAGE("고객 문의"),
    ADMIN_ORDER_PLACED("신규 주문"),
    ADMIN_ORDER_CANCELED("주문 취소");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 관리자 수신 알림인지. 팬아웃 검증과 화면 필터에 쓴다. */
    public boolean forAdmin() {
        return name().startsWith("ADMIN_");
    }
}
