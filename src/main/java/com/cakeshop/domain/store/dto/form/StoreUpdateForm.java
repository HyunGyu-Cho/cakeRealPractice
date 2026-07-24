package com.cakeshop.domain.store.dto.form;

import com.cakeshop.domain.store.dto.view.StoreView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 입력 전용 DTO다. DB 모델과 분리해 화면 검증 규칙이 영속 모델로 번지는 것을 막는다.
 */
@Getter
@Setter
public class StoreUpdateForm {

    @NotBlank(message = "매장명을 입력해 주세요.")
    @Size(max = 100, message = "매장명은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 1000, message = "매장 소개는 1,000자 이하여야 합니다.")
    private String description;

    @NotBlank(message = "주소를 입력해 주세요.")
    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "연락처 형식을 확인해 주세요.")
    private String phone;

    @NotNull(message = "평일 영업 시작 시간을 입력해 주세요.")
    private LocalTime weekdayOpenTime;

    @NotNull(message = "평일 영업 종료 시간을 입력해 주세요.")
    private LocalTime weekdayCloseTime;

    @NotNull(message = "주말 영업 시작 시간을 입력해 주세요.")
    private LocalTime weekendOpenTime;

    @NotNull(message = "주말 영업 종료 시간을 입력해 주세요.")
    private LocalTime weekendCloseTime;

    private Set<DayOfWeek> closedDays = new HashSet<>();

    @NotBlank(message = "픽업 장소를 입력해 주세요.")
    @Size(max = 255, message = "픽업 장소는 255자 이하여야 합니다.")
    private String pickupPlace;

    @NotNull(message = "픽업 시작 시간을 입력해 주세요.")
    private LocalTime pickupStartTime;

    @NotNull(message = "픽업 종료 시간을 입력해 주세요.")
    private LocalTime pickupEndTime;

    @NotNull(message = "픽업 시간 간격을 선택해 주세요.")
    @Min(value = 10, message = "픽업 간격은 10분 이상이어야 합니다.")
    @Max(value = 180, message = "픽업 간격은 180분 이하여야 합니다.")
    private Integer pickupIntervalMinutes;

    public static StoreUpdateForm from(StoreView store) {
        StoreUpdateForm form = new StoreUpdateForm();
        form.setName(store.name());
        form.setDescription(store.description());
        form.setAddress(store.address());
        form.setPhone(store.phone());
        form.setWeekdayOpenTime(store.weekdayOpenTime());
        form.setWeekdayCloseTime(store.weekdayCloseTime());
        form.setWeekendOpenTime(store.weekendOpenTime());
        form.setWeekendCloseTime(store.weekendCloseTime());
        form.setClosedDays(new HashSet<>(store.closedDays()));
        form.setPickupPlace(store.pickupPlace());
        form.setPickupStartTime(store.pickupStartTime());
        form.setPickupEndTime(store.pickupEndTime());
        form.setPickupIntervalMinutes(store.pickupIntervalMinutes());
        return form;
    }

    // 단일 필드 제약으로 표현할 수 없는 시간의 선후 관계는 객체 수준에서 검증한다.
    @AssertTrue(message = "평일 영업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isWeekdayTimeRangeValid() {
        return isIncreasing(weekdayOpenTime, weekdayCloseTime);
    }

    @AssertTrue(message = "주말 영업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isWeekendTimeRangeValid() {
        return isIncreasing(weekendOpenTime, weekendCloseTime);
    }

    @AssertTrue(message = "픽업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isPickupTimeRangeValid() {
        return isIncreasing(pickupStartTime, pickupEndTime);
    }

    private boolean isIncreasing(LocalTime start, LocalTime end) {
        return start == null || end == null || start.isBefore(end);
    }

    // null 유입 시 빈 컬렉션으로 방어하는 커스텀 로직이라 Lombok @Setter 대신 직접 정의한다.
    public void setClosedDays(Set<DayOfWeek> closedDays) {
        this.closedDays = closedDays == null ? new HashSet<>() : closedDays;
    }
}
