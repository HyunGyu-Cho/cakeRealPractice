package com.cakeshop.domain.order.entity;

import java.util.Set;

// 주문 상태 7개 — 일반 케이크·수제(주문제작) 케이크 공통 enum
// 일반: PAID → READY_FOR_PICKUP → PICKED_UP
// 수제: UNDER_REVIEW → IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP
public enum OrderStatus {

    UNDER_REVIEW,      // 수제 전용: 확인 중 (수제 주문 시작점)
    IN_PRODUCTION,     // 수제 전용: 제작 중
    REJECTED,          // 수제 전용: 반려 (최종)
    PAID,              // 일반 전용: 결제 완료 (일반 주문 시작점)
    READY_FOR_PICKUP,  // 공통: 픽업 대기
    PICKED_UP,         // 공통: 픽업 완료 (최종)
    CANCELED;          // 공통: 주문 취소 (최종)

    // 정의된 전이 외의 상태 변경은 허용하지 않는다
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case UNDER_REVIEW -> Set.of(IN_PRODUCTION, REJECTED, CANCELED).contains(next);
            case IN_PRODUCTION -> Set.of(READY_FOR_PICKUP, CANCELED).contains(next);
            case PAID -> Set.of(READY_FOR_PICKUP, CANCELED).contains(next);
            case READY_FOR_PICKUP -> Set.of(PICKED_UP, CANCELED).contains(next);
            case REJECTED, PICKED_UP, CANCELED -> false; // 최종 상태
        };
    }

    public boolean isFinal() {
        return this == REJECTED || this == PICKED_UP || this == CANCELED;
    }
}
