package com.cakeshop.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
import com.cakeshop.domain.store.dto.form.StoreUpdateForm;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.mapper.StoreMapper;
import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class StoreServiceTests {

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private FileStorageClient fileStorageClient;

    private StoreService storeService;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(storeMapper, fileStorageClient);
    }

    @Test
    void updateStoreUpdatesBaseInformationAndAllSevenDays() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        when(storeMapper.updateStore(any(Store.class))).thenReturn(1);
        StoreUpdateForm form = validForm();
        form.setClosedDays(EnumSet.of(DayOfWeek.SUNDAY));

        // 이미지 미첨부(null)는 기존 사진을 유지하는 흐름이다.
        storeService.updateStore(form, null);

        ArgumentCaptor<StoreBusinessHour> captor = ArgumentCaptor.forClass(StoreBusinessHour.class);
        verify(storeMapper, org.mockito.Mockito.times(7)).upsertBusinessHour(captor.capture());
        assertThat(captor.getAllValues()).hasSize(7);
        // 휴무일은 DB 제약에 맞춰 영업시간이 NULL 이어야 한다.
        assertThat(captor.getAllValues())
            .filteredOn(hour -> hour.getDayOfWeek() == DayOfWeek.SUNDAY)
            .singleElement()
            .satisfies(hour -> {
                assertThat(hour.isClosed()).isTrue();
                assertThat(hour.getOpenTime()).isNull();
                assertThat(hour.getCloseTime()).isNull();
            });
        // 영업일은 요일 구간에 맞는 시간이 유지되어야 한다(토요일=주말 시간).
        assertThat(captor.getAllValues())
            .filteredOn(hour -> hour.getDayOfWeek() == DayOfWeek.SATURDAY)
            .singleElement()
            .satisfies(hour -> {
                assertThat(hour.isClosed()).isFalse();
                assertThat(hour.getOpenTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(hour.getCloseTime()).isEqualTo(LocalTime.of(18, 0));
            });
    }

    @Test
    void updateStoreStoresNewImageAndDeletesPreviousOne() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202601/old.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.updateStore(any(Store.class))).thenReturn(1);
        when(fileStorageClient.store(any(), org.mockito.ArgumentMatchers.eq("store")))
            .thenReturn("/uploads/store/202607/new.jpg");
        MultipartFile image = new MockMultipartFile(
            "image", "cake.jpg", "image/jpeg", new byte[] {1, 2, 3});

        storeService.updateStore(validForm(), image);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).updateStore(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo("/uploads/store/202607/new.jpg");
        verify(fileStorageClient).delete("/uploads/store/202601/old.jpg");
    }

    @Test
    void updateStoreRejectsNonImageUpload() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        MultipartFile notImage = new MockMultipartFile(
            "image", "note.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> storeService.updateStore(validForm(), notImage))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.INVALID_IMAGE));
        verify(storeMapper, org.mockito.Mockito.never()).updateStore(any(Store.class));
    }

    @Test
    void addHolidayRejectsDuplicateDateWithBusinessErrorCode() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        LocalDate date = LocalDate.of(2026, 12, 25);
        when(storeMapper.existsHolidayDate(StoreService.DEFAULT_STORE_ID, date)).thenReturn(true);
        StoreHolidayForm form = new StoreHolidayForm();
        form.setHolidayDate(date);
        form.setReason("성탄절");

        assertThatThrownBy(() -> storeService.addHoliday(form))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.HOLIDAY_ALREADY_EXISTS));
    }

    private Store store() {
        Store store = new Store();
        store.setId(StoreService.DEFAULT_STORE_ID);
        store.setName("기존 매장");
        return store;
    }

    static StoreUpdateForm validForm() {
        StoreUpdateForm form = new StoreUpdateForm();
        form.setName("스위트온 케이크");
        form.setDescription("예약 케이크 전문점");
        form.setAddress("서울시 OO구");
        form.setPhone("02-0000-0000");
        form.setWeekdayOpenTime(LocalTime.of(10, 0));
        form.setWeekdayCloseTime(LocalTime.of(20, 0));
        form.setWeekendOpenTime(LocalTime.of(11, 0));
        form.setWeekendCloseTime(LocalTime.of(18, 0));
        form.setPickupPlace("1층 카운터");
        form.setPickupStartTime(LocalTime.of(10, 0));
        form.setPickupEndTime(LocalTime.of(19, 0));
        form.setPickupIntervalMinutes(60);
        return form;
    }
}
