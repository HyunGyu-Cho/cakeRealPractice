package com.cakeshop.domain.order.entity;

import java.util.Set;

// 주문 상태 11개 — 주문제작 승인 단계도 동일 enum에 포함
public enum OrderStatus {

    WAITING_APPROVAL,  // 주문제작 전용: 관리자 승인 대기
    APPROVED,          // 주문제작 전용: 승인됨 → 결제 진행 가능
    REJECTED,          // 주문제작 전용: 거절 (최종)
    PENDING_PAYMENT,   // 공통: 결제 대기 (일반 주문 시작점)
    PAID,              // 공통: 토스 결제 승인 완료
    ACCEPTED,          // 공통: 관리자 접수
    PREPARING,         // 공통: 제작·준비·포장
    READY,             // 공통: 픽업 준비 완료
    PICKED_UP,         // 공통: 인도 완료 (최종)
    CANCELED,          // 공통: 취소 완료 (최종)
    EXPIRED;           // 공통: 결제 시간 초과 (최종)

    // 정의된 전이 외의 상태 변경은 허용하지 않는다
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case WAITING_APPROVAL -> Set.of(APPROVED, REJECTED, CANCELED).contains(next);
            case APPROVED -> Set.of(PENDING_PAYMENT, CANCELED).contains(next);
            case PENDING_PAYMENT -> Set.of(PAID, EXPIRED, CANCELED).contains(next);
            case PAID -> Set.of(ACCEPTED, CANCELED).contains(next);
            case ACCEPTED -> Set.of(PREPARING, CANCELED).contains(next);      // 취소는 관리자만 (Service에서 검증)
            case PREPARING -> Set.of(READY, CANCELED).contains(next);          // 관리자만, 매장 정책 충족 시
            case READY -> Set.of(PICKED_UP, CANCELED).contains(next);          // 관리자만, 매장 정책 충족 시
            case REJECTED, PICKED_UP, CANCELED, EXPIRED -> false;              // 최종 상태
        };
    }

    public boolean isFinal() {
        return this == REJECTED || this == PICKED_UP || this == CANCELED || this == EXPIRED;
    }
}
