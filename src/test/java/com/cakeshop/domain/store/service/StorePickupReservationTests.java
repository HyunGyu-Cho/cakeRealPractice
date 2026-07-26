package com.cakeshop.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 픽업 정원(슬롯 1건)·유형별 예약 창·휴무일 충돌 차단.
 * 예약 현황은 order가 구현한 {@link PickupReservationPort}로만 읽는다(store는 orders를 모른다).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePickupReservationTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-07-25 13:20 KST (토요일)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T04:20:00Z"), SEOUL);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 7, 25);

    @Mock StoreMapper storeMapper;
    @Mock FileStorageClient fileStorageClient;
    @Mock PickupReservationPort pickupReservationPort;

    private StoreService service;

    @BeforeEach
    void setUp() {
        service = new StoreService(storeMapper, fileStorageClient, pickupReservationPort, CLOCK);
        when(storeMapper.findStoreById(1L)).thenReturn(Optional.of(store()));
        when(storeMapper.findBusinessHours(1L)).thenReturn(List.of(
            hour(DayOfWeek.SATURDAY, LocalTime.of(11, 0), LocalTime.of(17, 0), false),
            hour(DayOfWeek.SUNDAY, null, null, true)));
        when(storeMapper.findHolidays(1L)).thenReturn(List.of());
        when(pickupReservationPort.findReservedPickupAts(org.mockito.ArgumentMatchers.any()))
            .thenReturn(Set.of());
    }

    // ==================== 슬롯 정원 1건 ====================

    @Test
    void reservedSlotDisappearsFromTheList() {
        LocalDateTime taken = SATURDAY.atTime(15, 0);
        when(pickupReservationPort.findReservedPickupAts(List.of(SATURDAY)))
            .thenReturn(Set.of(taken));

        List<LocalDateTime> slots = service.getAvailablePickupSlots(SATURDAY, 0);

        assertThat(slots).isNotEmpty().doesNotContain(taken);
        // 인접 슬롯은 그대로 남는다 — 정원은 슬롯 단위다
        assertThat(slots).contains(SATURDAY.atTime(15, 30));
    }

    @Test
    void takenSlotIsRejectedWithItsOwnErrorCode() {
        LocalDateTime taken = SATURDAY.atTime(15, 0);
        when(pickupReservationPort.findReservedPickupAts(List.of(SATURDAY)))
            .thenReturn(Set.of(taken));

        // "선택할 수 없다"가 아니라 "이미 예약됐다"로 구분해 알린다
        assertThatThrownBy(() -> service.validatePickupAt(taken, 0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.PICKUP_SLOT_TAKEN);
    }

    @Test
    void storeWorksWithoutTheReservationPort() {
        // 포트가 없으면(부분 기동·단위 테스트) 정원 검사를 건너뛰고 기존 동작을 유지한다
        StoreService bare = new StoreService(storeMapper, fileStorageClient, null, CLOCK);

        assertThat(bare.getAvailablePickupSlots(SATURDAY, 0)).isNotEmpty();
    }

    // ==================== 휴무일 사유 구분 ====================

    @Test
    void closedDayReportsItsOwnReason() {
        LocalDate sunday = LocalDate.of(2026, 7, 26);

        assertThatThrownBy(() -> service.validatePickupAt(sunday.atTime(14, 0), 0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.PICKUP_DATE_CLOSED);
    }

    @Test
    void specificHolidayAlsoReportsClosed() {
        when(storeMapper.findHolidays(1L)).thenReturn(List.of(holiday(SATURDAY)));

        assertThatThrownBy(() -> service.validatePickupAt(SATURDAY.atTime(14, 0), 0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.PICKUP_DATE_CLOSED);
    }

    // ==================== 유형별 예약 창 ====================

    @Test
    void defaultWindowIsFourteenDays() {
        // 7/25 + 14일 = 8/8(토). 그 다음 토요일 8/15는 창 밖이다.
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 8, 8), 0)).isNotEmpty();
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 8, 15), 0)).isEmpty();
    }

    @Test
    void longerWindowOpensDatesBeyondTheDefault() {
        // 주문제작은 재고를 묶지 않으므로 더 긴 창을 넘긴다
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 8, 15), 0, 90)).isNotEmpty();
        // 창을 늘려도 상한 자체는 남는다
        assertThat(service.getAvailablePickupSlots(LocalDate.of(2026, 12, 5), 0, 90)).isEmpty();
    }

    // ==================== 휴무일 충돌 차단 ====================

    @Test
    void holidayIsBlockedWhenPickupsAreScheduled() {
        when(storeMapper.existsHolidayDate(1L, SATURDAY)).thenReturn(false);
        when(pickupReservationPort.countReservedPickups(SATURDAY)).thenReturn(3L);

        assertThatThrownBy(() -> service.addHoliday(holidayForm(SATURDAY)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.HOLIDAY_HAS_PICKUP)
            // 관리자가 몇 건을 정리해야 하는지 알 수 있어야 한다
            .hasMessageContaining("3건");
        verify(storeMapper, never()).insertHoliday(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void holidayIsAllowedWhenNoPickupsAreScheduled() {
        when(storeMapper.existsHolidayDate(1L, SATURDAY)).thenReturn(false);
        when(pickupReservationPort.countReservedPickups(SATURDAY)).thenReturn(0L);

        service.addHoliday(holidayForm(SATURDAY));

        verify(storeMapper).insertHoliday(org.mockito.ArgumentMatchers.any());
    }

    // ==================== 헬퍼 ====================

    private StoreHolidayForm holidayForm(LocalDate date) {
        StoreHolidayForm form = new StoreHolidayForm();
        form.setHolidayDate(date);
        form.setReason("정기 점검");
        return form;
    }

    private Store store() {
        Store store = new Store();
        store.setId(1L);
        store.setName("케이크샵");
        store.setPickupStartTime(LocalTime.of(10, 0));
        store.setPickupEndTime(LocalTime.of(17, 0));
        store.setPickupIntervalMinutes(30);
        return store;
    }

    private StoreBusinessHour hour(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        StoreBusinessHour hour = new StoreBusinessHour();
        hour.setStoreId(1L);
        hour.setDayOfWeek(day);
        hour.setOpenTime(open);
        hour.setCloseTime(close);
        hour.setClosed(closed);
        return hour;
    }

    private StoreHoliday holiday(LocalDate date) {
        StoreHoliday holiday = new StoreHoliday();
        holiday.setStoreId(1L);
        holiday.setHolidayDate(date);
        return holiday;
    }
}
