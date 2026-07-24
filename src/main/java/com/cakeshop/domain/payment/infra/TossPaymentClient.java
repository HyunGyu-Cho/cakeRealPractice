package com.cakeshop.domain.payment.infra;

import org.springframework.stereotype.Component;

// 토스 승인·취소·조회 API 클라이언트 — 작업별 UUID를 Idempotency-Key로 사용
@Component
public class TossPaymentClient {

    public void approve(String paymentKey, String orderId, long amount, String idempotencyKey) {
        // TODO
    }

    public void cancel(String paymentKey, String reason, String idempotencyKey) {
        // TODO
    }

    public void find(String paymentKey) {
        // TODO: 상태 대조용 결제 조회
    }
}
