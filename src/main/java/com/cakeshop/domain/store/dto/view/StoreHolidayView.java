package com.cakeshop.domain.store.dto.view;

import java.time.LocalDate;

/** 관리자 매장 화면에 노출하는 휴무일 조회 DTO. */
public record StoreHolidayView(
    Long id,
    LocalDate holidayDate,
    String reason
) {
}
