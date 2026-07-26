package com.cakeshop.domain.notification.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 알림 한 건의 전달 이력. 업무 트랜잭션에서는 REQUESTED 행 INSERT만 하고,
 * 실제 전송 결과(SENT/FAILED/ABANDONED)는 커밋 후 별도 트랜잭션에서 기록한다.
 * {@code requested_at}/{@code created_at}은 DDL DEFAULT에 위임한다.
 */
@Getter
@Setter
public class NotificationDelivery {

    private Long id;
    private Long notificationId;
    private DeliveryChannel channel;
    /** 전송 시점에 해석해 채운다(업무 트랜잭션에서 회원 조회 금지). */
    private String recipient;
    private DeliveryStatus status;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private String failureCode;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
