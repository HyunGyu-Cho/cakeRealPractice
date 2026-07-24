package com.cakeshop.domain.payment.entity;

// 토스 결제 상태 — OrderStatus와 분리, 주문 enum에 섞지 않는다
public enum PaymentStatus {
    READY,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED
}
