package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

/** 상품 옵션 그룹(크기·맛·색상 등). selection_type은 상태가 아니라 종류다. */
@Getter
@Setter
public class ProductOptionGroup {
    private Long id;
    private Long productId;
    private String name;
    private Boolean required;
    private String selectionType;
    private Integer sortOrder;
}
