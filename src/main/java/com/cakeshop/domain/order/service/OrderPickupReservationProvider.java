package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.store.service.PickupReservationPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * store가 정의한 {@link PickupReservationPort}의 order 쪽 구현.
 *
 * <p>주문을 소유한 도메인이 예약 현황을 알려주는 형태라 store는 {@code orders}를 몰라도 되고,
 * order → store 단방향이 유지된다.
 *
 * <p>"예약됨"의 기준은 <b>취소·반려되지 않은 주문</b>이다. 취소된 주문의 슬롯은 다시 열려야 한다.
 */
@Component
public class OrderPickupReservationProvider implements PickupReservationPort {

    private final OrderMapper orderMapper;

    public OrderPickupReservationProvider(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public long countReservedPickups(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return orderMapper.countActivePickupsOn(date);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDateTime> findReservedPickupAts(Collection<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Set.of();
        }
        List<LocalDateTime> reserved = orderMapper.findActivePickupAts(dates);
        return new LinkedHashSet<>(reserved);
    }
}
