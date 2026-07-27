package com.cakeshop.domain.payment.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 제공자가 보낸 웹훅 1건. {@code eventId}(전송 ID) UNIQUE가 재전송을 막는다. */
@Getter
@Setter
public class WebhookEvent {
    private Long id;
    private String eventId;
    private String eventType;
    private String paymentKey;
    private String tossOrderId;
    /** 제공자 원본 상태 문자열. 우리 enum으로 좁히기 전 값이다. */
    private String providerStatus;
    private String payload;
    private String processStatus;
    private String failReason;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
