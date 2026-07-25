package com.cakeshop.domain.payment.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Payment {
    private Long id;
    private Long orderId;
    private String tossOrderId;
    private String paymentKey;
    private String idempotencyKey;
    private String method;
    private Long amount;
    private String status;
    private String providerStatus;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
