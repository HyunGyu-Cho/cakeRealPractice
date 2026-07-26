package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/** 관리자 쿠폰 목록 한 줄. {@code expired}·{@code soldOut}은 저장하지 않는 파생값이다. */
public record CouponAdminListView(
    Long id,
    String name,
    String discountType,
    String discountTypeLabel,
    long discountValue,
    String discountLabel,
    long minimumOrderAmount,
    Long maximumDiscountAmount,
    int totalQuantity,
    int issuedQuantity,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    String status,
    String statusLabel,
    boolean expired,
    boolean soldOut) {
}
