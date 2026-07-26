package com.cakeshop.domain.order.dto.view;

/**
 * 기간 주문 요약. 매출이 아니라 <b>주문 금액</b>(orders.final_amount)까지만 담는다 —
 * 실제 순매출은 payment 도메인이 계산한다.
 *
 * <p>세 건수의 기준 시각이 서로 다르다: 주문은 {@code created_at},
 * 완료는 {@code picked_up_at}, 취소는 {@code canceled_at}이 기간 안에 든 주문을 센다.
 * 현재 상태 스냅샷(검토 대기·제작 중)은 기간 지표가 아니므로 여기 담지 않고
 * {@code countByStatus}로 따로 센다.
 */
public record OrderStatsView(
    long orderCount,
    long completedCount,
    long canceledCount,
    long orderAmount
) {
    public static OrderStatsView empty() {
        return new OrderStatsView(0, 0, 0, 0);
    }

    /** 평균 주문 금액(원 단위 절삭). 주문이 없으면 0이다. */
    public long averageOrderAmount() {
        return orderCount == 0 ? 0 : orderAmount / orderCount;
    }
}
