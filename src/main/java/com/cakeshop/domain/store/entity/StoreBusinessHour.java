package com.cakeshop.domain.store.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

/** 요일별 영업시간. 휴무 여부를 시간 문자열에 섞지 않고 별도 컬럼으로 관리한다. */
@Getter
@Setter
public class StoreBusinessHour {

    private Long id;
    private Long storeId;
    private DayOfWeek dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;

}
