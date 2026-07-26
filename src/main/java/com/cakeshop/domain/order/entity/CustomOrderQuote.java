package com.cakeshop.domain.order.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 주문제작 견적 한 회차. 재견적 시 수정하지 않고 새 version 행을 넣어 스냅샷을 보존한다. */
@Getter
@Setter
public class CustomOrderQuote {
    private Long id;
    private Long orderId;
    private Integer version;
    private Long quotedAmount;
    private LocalDate producibleDate;
    private String adminNote;
    private Long issuedBy;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
