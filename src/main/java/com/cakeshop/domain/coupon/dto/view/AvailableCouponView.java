package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/** 체크아웃 쿠폰 선택지. {@code discountAmount}는 이 주문 금액 기준으로 서버가 계산한 값이다. */
public record AvailableCouponView(
    Long memberCouponId,
    String couponName,
    String discountLabel,
    long discountAmount,
    long minimumOrderAmount,
    LocalDateTime expiresAt) {
}
