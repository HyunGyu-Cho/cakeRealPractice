package com.cakeshop.domain.order.dto.view;

/**
 * 결제 링크로 결제할 수 있다고 검증된 주문제작 건. payment 도메인이 결제를 준비·확정할 때 필요한 값만 담는다.
 *
 * <p>payment는 주문 테이블을 직접 읽지 않고 이 View로만 주문을 안다(절대규칙 — 도메인 격리).
 *
 * @param amount 견적 확정 금액. 쿠폰 할인을 적용하기 전의 원가다.
 */
public record CustomOrderPayableView(Long orderId, String orderNumber, Long memberId, long amount) {
}
