package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

/** 주문제작 요청서의 참고 이미지. FileStorageClient가 저장한 경로만 담는다. */
@Getter
@Setter
public class OrderItemImage {
    private Long id;
    private Long orderItemId;
    private String imageUrl;
    private Integer sortOrder;
}
