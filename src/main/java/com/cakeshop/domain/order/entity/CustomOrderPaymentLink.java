package com.cakeshop.domain.order.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 견적 1건당 1개인 일회성 결제 링크. */
@Getter
@Setter
public class CustomOrderPaymentLink {
    private Long id;
    private Long quoteId;
    private String token;
    private Long amount;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;
    private String status;
    private LocalDateTime createdAt;
}
