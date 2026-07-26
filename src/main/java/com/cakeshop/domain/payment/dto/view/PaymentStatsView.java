package com.cakeshop.domain.payment.dto.view;

/**
 * 기간 결제 요약. 순매출은 승인된 결제 금액에서 완료된 취소 금액을 뺀 값이다.
 * 결제는 {@code approved_at}, 환불은 {@code canceled_at} 기준으로 기간을 잡는다.
 */
public record PaymentStatsView(
    long paymentCount,
    long paidAmount,
    long refundedAmount
) {
    public static PaymentStatsView empty() {
        return new PaymentStatsView(0, 0, 0);
    }

    /** 순매출. 환불이 더 많아도 음수를 그대로 노출한다(집계 왜곡을 숨기지 않는다). */
    public long netSalesAmount() {
        return paidAmount - refundedAmount;
    }
}
