package com.cakeshop.domain.coupon.entity;

import java.util.Set;

/**
 * 쿠폰(캠페인) 상태. 값 정본은 {@code docs/specs/coupon.md} 2장이며 DDL {@code chk_coupons_status}와 짝이다.
 *
 * <p><b>만료·소진은 상태가 아니다.</b> 만료는 {@code expires_at}, 소진은
 * {@code issued_quantity >= total_quantity}에서 나오는 파생값이라 저장하지 않는다.
 */
public enum CouponStatus {

    ACTIVE("발급 중"),      // 시작 상태 (DDL DEFAULT)
    SUSPENDED("발급 중지"),
    ENDED("종료");          // 최종

    private final String label;

    CouponStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(CouponStatus next) {
        return switch (this) {
            case ACTIVE -> Set.of(SUSPENDED, ENDED).contains(next);
            case SUSPENDED -> Set.of(ACTIVE, ENDED).contains(next);
            case ENDED -> false;
        };
    }
}
