package com.cakeshop.domain.order.dto.view;

/** 시간대별 픽업 건수. hour는 0~23이다. */
public record PickupHourCountView(
    int hour,
    long pickupCount
) {
}
