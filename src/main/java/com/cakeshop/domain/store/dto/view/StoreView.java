package com.cakeshop.domain.store.dto.view;

import com.cakeshop.domain.store.entity.StoreHoliday;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** 관리자 화면에 필요한 여러 테이블의 조회 결과를 하나로 조합한 읽기 DTO다. */
public record StoreView(
    Long id,
    String name,
    String description,
    String imageUrl,
    String address,
    String phone,
    LocalTime weekdayOpenTime,
    LocalTime weekdayCloseTime,
    LocalTime weekendOpenTime,
    LocalTime weekendCloseTime,
    Set<DayOfWeek> closedDays,
    String pickupPlace,
    LocalTime pickupStartTime,
    LocalTime pickupEndTime,
    Integer pickupIntervalMinutes,
    List<StoreHoliday> holidays
) {
}
