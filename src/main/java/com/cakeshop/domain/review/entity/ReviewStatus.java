package com.cakeshop.domain.review.entity;

/**
 * 후기 노출 상태. DDL {@code chk_reviews_status}와 짝이다.
 *
 * <p>값이 둘뿐인 이유는 <b>삭제를 상태로 두지 않기 때문</b>이다. 소프트 삭제로 행을 남기면
 * {@code uk_reviews_order_item}이 잡혀 있어 그 주문 항목에 다시는 후기를 쓸 수 없다.
 * 삭제는 행 제거로 처리한다(스펙 6장 규칙 4).
 *
 * <p>둘 다 최종 상태가 아니다 — 관리자가 숨겼다 다시 보일 수 있다.
 */
public enum ReviewStatus {

    VISIBLE("공개"),   // 시작 상태 (DDL DEFAULT)
    HIDDEN("숨김");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(ReviewStatus next) {
        return this != next;
    }
}
