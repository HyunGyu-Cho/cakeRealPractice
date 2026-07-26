package com.cakeshop.domain.product.dto.form;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** 관리자 목록의 GET 검색 조건 바인딩 전용. 허용값 밖 입력은 전체(null)로 흡수한다. */
@Getter
@Setter
public class AdminProductSearchForm {

    private static final Set<String> TYPES = Set.of("GENERAL", "CUSTOM", "SAME_DAY", "SEASON");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> STOCKS = Set.of("IN_STOCK", "SOLD_OUT");

    private String keyword;
    private String type;
    private String status;
    private String stock;
    private Integer page;

    public String getNormalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public String getNormalizedType() {
        return type != null && TYPES.contains(type.toUpperCase()) ? type.toUpperCase() : null;
    }

    public String getNormalizedStatus() {
        return status != null && STATUSES.contains(status.toUpperCase()) ? status.toUpperCase() : null;
    }

    public String getNormalizedStock() {
        return stock != null && STOCKS.contains(stock.toUpperCase()) ? stock.toUpperCase() : null;
    }
}
