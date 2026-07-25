package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

/** 1차에서는 상품당 대표 1행(sort_order = 0)만 사용한다. */
@Getter
@Setter
public class ProductImage {

    private Long id;
    private Long productId;
    private String imageUrl;
    private Integer sortOrder;
}
