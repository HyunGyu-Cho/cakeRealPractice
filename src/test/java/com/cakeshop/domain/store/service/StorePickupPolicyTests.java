package com.cakeshop.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.domain.store.entity.StoreHoliday;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.mapper.StoreMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorePickupPolicyTests {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-07-25T04:20:00Z"), SEOUL); // 13:20

    @Mock StoreMapper storeMapper;
    @Mock FileStorageClient fileStorageClient;

    private StoreService service;

    @BeforeEach
    void setUp() {
        service = new StoreService(storeMapper, fileStorageClient, CLOCK);
        when(storeMapper.findStoreById(1L)).thenReturn(Optional.of(store()));
        when(storeMapper.findBusinessHours(1L)).thenReturn(List.of(hour(
            DayOfWeek.SATURDAY, LocalTime.of(11, 0), LocalTime.of(17, 0), false)));
        when(storeMapper.findHolidays(1L)).thenReturn(List.of());
    }

    @Test
    void appliesPreparationAndFourteenDayBoundaries() {
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 7, 26), 2)).isEmpty();
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 7, 27), 2))
            .isEmpty(); // Monday hour is absent
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 8, 8), 14))
            .isNotEmpty();
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 8, 9), 0)).isEmpty();
    }

    @Test
    void intersectsBusinessHoursAndSkipsPastTodaySlots() {
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 7, 25), 0))
            .containsExactly(
                LocalDateTime.of(2026, 7, 25, 14, 0),
                LocalDateTime.of(2026, 7, 25, 15, 0),
                LocalDateTime.of(2026, 7, 25, 16, 0))
            .doesNotContain(LocalDateTime.of(2026, 7, 25, 13, 0));
    }

    @Test
    void rejectsHolidayAndIntervalMismatch() {
        StoreHoliday holiday = new StoreHoliday();
        holiday.setHolidayDate(LocalDate.of(2026, 7, 25));
        when(storeMapper.findHolidays(1L)).thenReturn(List.of(holiday));
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 7, 25), 0)).isEmpty();

        when(storeMapper.findHolidays(1L)).thenReturn(List.of());
        assertThatThrownBy(() ->
            service.validatePickupAt(LocalDateTime.of(2026, 7, 25, 14, 30), 0))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(StoreErrorCode.INVALID_PICKUP_AT));
    }

    private Store store() {
        Store store = new Store();
        store.setId(1L);
        store.setPickupStartTime(LocalTime.of(10, 0));
        store.setPickupEndTime(LocalTime.of(19, 0));
        store.setPickupIntervalMinutes(60);
        return store;
    }

    private StoreBusinessHour hour(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        StoreBusinessHour hour = new StoreBusinessHour();
        hour.setDayOfWeek(day);
        hour.setOpenTime(open);
        hour.setCloseTime(close);
        hour.setClosed(closed);
        return hour;
    }
}
