package com.cakeshop.domain.product.entity;

// 판매 스위치 2개 — 값 정본은 docs/specs/product.md. 품절·재고부족은 stock_quantity 파생값이라 여기 없다.
public enum ProductStatus {

    ACTIVE("판매 중"),
    INACTIVE("판매 중지");

    private final String label;

    ProductStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean matches(String status) {
        return name().equals(status);
    }
}
