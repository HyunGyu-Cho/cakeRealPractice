package com.cakeshop.domain.payment.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentCancellation {
    private Long id;
    private Long paymentId;
    private String idempotencyKey;
    private Long cancelAmount;
    private String cancelReason;
    private String status;
    private String transactionKey;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
}
