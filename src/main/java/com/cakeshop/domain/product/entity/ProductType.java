package com.cakeshop.domain.product.entity;

// 상품 종류 — status가 아니라 type이지만 저장 규칙(영문 enum 이름)은 동일하다.
// 1차에서는 categories.code와 1:1이며, 주문제작(CUSTOM)만 재고를 관리하지 않는다.
public enum ProductType {

    GENERAL("일반 케이크"),
    CUSTOM("주문 제작"),
    SAME_DAY("당일 픽업"),
    SEASON("시즌 상품");

    private final String label;

    ProductType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isStockManaged() {
        return this != CUSTOM;
    }
}
