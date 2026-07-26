package com.cakeshop.domain.store.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/**
 * 픽업 예약 현황을 store에 알려주는 포트. <b>구현은 order 도메인이 제공한다.</b>
 *
 * <p>store가 {@code orders}를 직접 조회하면 order → store 위에 store → order가 얹혀 순환이 된다.
 * 그래서 store는 자기가 정의한 이 인터페이스에만 의존하고, 주문을 소유한 order가 구현체를 끼운다
 * (의존성 역전). 시그니처 변경 시 store 오너와 주환의 합의가 필요하다.
 */
public interface PickupReservationPort {

    /** 해당 날짜에 픽업이 예정된(취소·반려되지 않은) 주문 수. 휴무일 지정 가능 여부 판정에 쓴다. */
    long countReservedPickups(LocalDate date);

    /** 기간 안에서 이미 예약된 픽업 시각. 슬롯 정원(1건) 필터에 쓴다. */
    Set<LocalDateTime> findReservedPickupAts(Collection<LocalDate> dates);
}
