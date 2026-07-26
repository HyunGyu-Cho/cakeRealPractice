package com.cakeshop.domain.order.dto.view;

/** 요청 시점에 저장된 옵션 스냅샷. */
public record CustomOrderOptionView(
    String groupName,
    String optionName,
    long additionalPrice
) {
}
