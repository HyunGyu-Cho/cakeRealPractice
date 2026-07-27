package com.cakeshop.domain.payment.infra;

import java.time.LocalDateTime;

/**
 * 승인 결과. 외부 응답을 그대로 노출하지 않고 저장에 필요한 값만 담는다.
 *
 * @param providerStatus 제공자 원본 상태 문자열. {@code payments.provider_status}에 그대로 남긴다.
 */
public record PaymentApproval(String paymentKey, String tossOrderId, long amount,
                              String providerStatus, LocalDateTime approvedAt) {
}
