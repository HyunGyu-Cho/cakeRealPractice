package com.cakeshop.domain.order.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 견적 한 회차의 화면 출력. 재견적 이력을 그대로 보여주기 위해 회차별로 만든다. */
public record CustomOrderQuoteView(
    Long id,
    int version,
    long quotedAmount,
    LocalDate producibleDate,
    String adminNote,
    String status,
    String statusLabel,
    LocalDateTime sentAt,
    LocalDateTime acceptedAt
) {
}
