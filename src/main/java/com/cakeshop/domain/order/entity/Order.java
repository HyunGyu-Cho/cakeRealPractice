package com.cakeshop.domain.order.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {
    private Long id;
    private String orderNumber;
    private Long memberId;
    private String ordererName;
    private String ordererPhone;
    private String pickupName;
    private String pickupPhone;
    private Long originalAmount;
    private Long discountAmount;
    private Long finalAmount;
    private String status;
    private LocalDateTime pickupAt;
    private String requestMessage;
    private String rejectReason;
    private LocalDateTime rejectedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private String canceledBy;
    private LocalDateTime pickupReminderSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
