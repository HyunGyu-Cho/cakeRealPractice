package com.cakeshop.domain.order.entity;

import java.util.Set;

/**
 * 주문제작 결제 링크의 상태.
 * {@code EXPIRED}는 만료 시각의 파생값을 확정 기록한 것이고, 판정 자체는 {@code expires_at}으로 한다.
 */
public enum PaymentLinkStatus {

    ISSUED("발급됨"),       // 시작 상태 (DDL DEFAULT)
    USED("결제 완료"),      // 최종
    EXPIRED("만료됨"),      // 최종
    REVOKED("무효화됨");    // 최종 — 재견적·주문 취소로 회수

    private final String label;

    PaymentLinkStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(PaymentLinkStatus next) {
        return switch (this) {
            case ISSUED -> Set.of(USED, EXPIRED, REVOKED).contains(next);
            case USED, EXPIRED, REVOKED -> false;
        };
    }

    public boolean isFinal() {
        return this != ISSUED;
    }
}
