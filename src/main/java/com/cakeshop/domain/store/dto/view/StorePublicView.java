package com.cakeshop.domain.store.dto.view;

/** 고객 화면은 수정용 세부값 대신 표시 가능한 문자열만 전달받는다. */
public record StorePublicView(
    String name,
    String description,
    String imageUrl,
    String address,
    String phone,
    String businessHours,
    String closedDays,
    String pickupPlace,
    String pickupHours
) {
}
