package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

/** 주문 시점 옵션 스냅샷. 이후 상품 옵션이 바뀌어도 주문 내역은 그대로 남는다. */
@Getter
@Setter
public class OrderItemOption {
    private Long id;
    private Long orderItemId;
    private Long productOptionId;
    private String optionGroupName;
    private String optionName;
    private Long additionalPrice;
}
