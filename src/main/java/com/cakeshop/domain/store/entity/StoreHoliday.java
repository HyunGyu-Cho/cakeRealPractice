package com.cakeshop.domain.store.entity;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 정기 휴무와 별개로 관리자가 추가하는 특정 날짜 휴무다. */
@Getter
@Setter
public class StoreHoliday {

    private Long id;
    private Long storeId;
    private LocalDate holidayDate;
    private String reason;

}
