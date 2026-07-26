package com.cakeshop.domain.product.entity;

/**
 * 상품 옵션 노출 스위치. 관리자가 껐다 켤 수 있어 최종 상태가 없다.
 * product 스펙이 order(수제) 차례로 넘긴 ☐ 항목을 V13에서 확정했다.
 */
public enum ProductOptionStatus {

    ACTIVE("판매 중"),
    INACTIVE("판매 중지");

    private final String label;

    ProductOptionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean matches(String value) {
        return name().equals(value);
    }
}
