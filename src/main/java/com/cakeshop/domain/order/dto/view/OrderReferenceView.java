package com.cakeshop.domain.order.dto.view;

public record OrderReferenceView(
    Long id,
    String orderNumber,
    Long memberId
) {
}
