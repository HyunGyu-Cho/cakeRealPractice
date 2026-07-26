package com.cakeshop.domain.notification.entity;

import java.util.Set;

/**
 * 알림 전달 상태 4개 — 커밋 후 즉시 푸시 → 실패분 스케줄러 재시도(최대 3회).
 * <pre>
 * REQUESTED(전달 요청) → SENT(전송 성공, 최종)
 *                      → FAILED(전송 실패, 재시도 대기)
 * FAILED → SENT | FAILED(재시도 또 실패) | ABANDONED(재시도 소진, 최종)
 * </pre>
 * DB {@code CHECK}는 값 집합만 지키고 전이는 이 enum이 소유한다(conventions.md).
 */
public enum DeliveryStatus {

    REQUESTED("전달 요청"),
    SENT("전송 성공"),
    FAILED("전송 실패"),
    ABANDONED("재시도 소진");

    private final String label;

    DeliveryStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 정의된 전이 외의 상태 변경은 허용하지 않는다. */
    public boolean canTransitionTo(DeliveryStatus next) {
        return switch (this) {
            case REQUESTED -> Set.of(SENT, FAILED).contains(next);
            // 재시도 실패는 자기 전이다. 상태는 그대로 두고 retry_count만 올린다.
            case FAILED -> Set.of(SENT, FAILED, ABANDONED).contains(next);
            case SENT, ABANDONED -> false; // 최종 상태
        };
    }

    public boolean isFinal() {
        return this == SENT || this == ABANDONED;
    }
}
