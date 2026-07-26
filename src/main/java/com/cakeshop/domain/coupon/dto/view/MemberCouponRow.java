package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/**
 * {@code member_coupons} + {@code coupons} 조인 결과 한 행. 두 테이블 모두 coupon 도메인 소유라 조인해도 된다.
 *
 * <p>화면에 직접 노출하지 않는다 — 서비스가 만료 여부 같은 파생값을 붙여 View로 바꾼다.
 */
public record MemberCouponRow(
    Long memberCouponId,
    Long couponId,
    String couponName,
    String discountType,
    Long discountValue,
    Long minimumOrderAmount,
    Long maximumDiscountAmount,
    LocalDateTime startsAt,
    LocalDateTime expiresAt,
    String couponStatus,
    String status,
    Long appliedOrderId,
    LocalDateTime issuedAt,
    LocalDateTime usedAt) {
}
