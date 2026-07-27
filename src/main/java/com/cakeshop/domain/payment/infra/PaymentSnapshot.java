package com.cakeshop.domain.payment.infra;

/** 상태 대조용 조회 결과. 제공자가 보는 현재 상태다. */
public record PaymentSnapshot(String paymentKey, String tossOrderId, long amount,
                              String providerStatus) {
}
