package com.cakeshop.domain.order.entity;

import java.util.Set;

/**
 * 견적 회차의 상태. <b>주문 상태가 아니다</b> — {@link OrderStatus} 7개는 팀 확정값이라
 * 견적 진행(발송·수락·대체)을 담을 자리가 없어 견적 테이블이 따로 소유한다.
 * 주문은 결제 전까지 {@code UNDER_REVIEW}를 유지하며, 견적을 몇 번 주고받아도 움직이지 않는다.
 */
public enum QuoteStatus {

    SENT("견적 발송"),        // 시작 상태 (DDL DEFAULT)
    ACCEPTED("수락 완료"),    // 최종
    SUPERSEDED("재견적됨");   // 최종 — 새 회차가 발행되며 밀려난 이전 견적

    private final String label;

    QuoteStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(QuoteStatus next) {
        return switch (this) {
            case SENT -> Set.of(ACCEPTED, SUPERSEDED).contains(next);
            case ACCEPTED, SUPERSEDED -> false;
        };
    }

    public boolean isFinal() {
        return this != SENT;
    }
}
