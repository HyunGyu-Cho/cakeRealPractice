package com.cakeshop.global.common.stats;

/**
 * 통계 집계 단위. DB에 저장하지 않는 조회 파라미터이므로 status가 아니다.
 * 여러 도메인(order·payment)의 집계 쿼리가 함께 쓰기 때문에 global/common에 둔다.
 */
public enum StatsPeriod {
    DAY("일별"),
    WEEK("주별"),
    MONTH("월별");

    private final String label;

    StatsPeriod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 화면에서 넘어온 값이 비었거나 알 수 없으면 일별로 떨어뜨린다. */
    public static StatsPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DAY;
        }
    }
}
