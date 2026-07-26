package com.cakeshop.domain.product.dto.view;

/** 화면 출력 전용 옵션 값. 추가 금액은 서버가 DB에서 읽은 값이 정본이다. */
public record ProductOptionView(
    Long id,
    String name,
    long additionalPrice
) {
}
