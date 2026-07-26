package com.cakeshop.domain.statistics.dto.form;

import com.cakeshop.global.common.stats.StatsPeriod;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 통계 조회 조건. 기간이 비어 들어오면 최근 30일로 채운다.
 * 교차 검증(시작일 ≤ 종료일, 최대 범위)은 관례대로 여기 @AssertTrue에 둔다.
 */
@Getter
@Setter
public class StatisticsSearchForm {
    /** 한 번에 볼 수 있는 최대 기간. 윤년을 포함해도 1년이 들어오도록 366일로 잡았다. */
    public static final int MAX_RANGE_DAYS = 366;

    private static final int DEFAULT_RANGE_DAYS = 30;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String period;

    /** 비어 있는 기간을 오늘 기준 최근 30일로 채운다. 검증 전에 호출한다. */
    public void applyDefaults(LocalDate today) {
        if (endDate == null) {
            endDate = today;
        }
        if (startDate == null) {
            startDate = endDate.minusDays(DEFAULT_RANGE_DAYS - 1L);
        }
    }

    public StatsPeriod resolvedPeriod() {
        return StatsPeriod.from(period);
    }

    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isPeriodOrdered() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    @AssertTrue(message = "한 번에 조회할 수 있는 기간은 최대 366일입니다.")
    public boolean isRangeWithinLimit() {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return true;
        }
        return !startDate.plusDays(MAX_RANGE_DAYS - 1L).isBefore(endDate);
    }
}
