package com.cakeshop.domain.coupon.dto.view;

/**
 * 서버가 계산한 할인 결과. order·payment는 클라이언트가 보낸 금액 대신 이 값만 쓴다.
 *
 * @param memberCouponId 적용한 쿠폰. 미적용이면 {@code null}
 */
public record CouponDiscount(Long memberCouponId, long discountAmount, long finalAmount) {

    /** 쿠폰을 고르지 않은 정상 경로. */
    public static CouponDiscount none(long originalAmount) {
        return new CouponDiscount(null, 0L, originalAmount);
    }

    public boolean applied() {
        return memberCouponId != null;
    }
}
