package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOption {
    private Long id;
    private Long optionGroupId;
    private String name;
    private Long additionalPrice;
    private String status;
    private Integer sortOrder;
}
