package com.cakeshop.domain.payment.infra;

import java.time.LocalDateTime;

/**
 * 취소 결과.
 *
 * @param transactionKey 제공자가 매긴 취소 거래 키. {@code payment_cancellations.transaction_key}에 저장한다.
 */
public record PaymentCancel(String transactionKey, String providerStatus,
                            LocalDateTime canceledAt) {
}
