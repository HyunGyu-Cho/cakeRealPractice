package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/**
 * 쿠폰함 카드 한 장.
 *
 * <p>{@code state}는 저장값이 아니라 {@code status}(2개) + 만료 파생값을 합친 <b>화면 분류</b>다
 * (사용 가능 / 사용 완료 / 기간 만료 탭).
 */
public record MyCouponView(
    Long memberCouponId,
    String couponName,
    String discountLabel,
    long minimumOrderAmount,
    LocalDateTime expiresAt,
    LocalDateTime usedAt,
    String state,
    String stateLabel) {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";
}
