package com.cakeshop.domain.product.dto.form;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 고객 목록의 GET 검색 조건 바인딩 전용. 검증 실패로 화면을 막지 않고
 * 허용값 밖 입력은 정규화 메서드가 null(전체)·기본값으로 흡수한다.
 */
@Getter
@Setter
public class ProductSearchForm {

    private static final Set<String> TYPES = Set.of("NORMAL", "CUSTOM", "SAME_DAY", "SEASON");
    private static final Set<String> SALES = Set.of("ON_SALE", "SOLD_OUT", "INACTIVE");
    private static final Set<String> SORTS = Set.of("latest", "priceAsc", "priceDesc", "popular");

    private String type;
    private Long minPrice;
    private Long maxPrice;
    private String sale;
    private Boolean pickupToday;
    private String keyword;
    private String sort;
    private Integer page;

    public String getNormalizedType() {
        return type != null && TYPES.contains(type.toUpperCase()) ? type.toUpperCase() : null;
    }

    public String getNormalizedSale() {
        return sale != null && SALES.contains(sale.toUpperCase()) ? sale.toUpperCase() : null;
    }

    public String getNormalizedSort() {
        return sort != null && SORTS.contains(sort) ? sort : "latest";
    }

    public String getNormalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public boolean isPickupTodayOnly() {
        return Boolean.TRUE.equals(pickupToday);
    }
}
