package com.cakeshop.domain.coupon.entity;

/**
 * 회원이 보유한 쿠폰 1장의 상태. DDL {@code chk_member_coupons_status}와 짝이다.
 *
 * <p>값이 둘뿐인 이유는 <b>만료를 저장하지 않기 때문</b>이다. 만료는 쿠폰의 {@code expires_at}과
 * 현재 시각의 파생값이라, 상태로 들고 있으면 시각이 지날 때마다 전 행을 갱신하는 배치가 필요하고
 * 그 배치가 밀리는 순간 화면과 DB가 어긋난다.
 *
 * <p>{@code USED}는 최종이 아니다 — 주문이 취소되면 {@code AVAILABLE}로 되돌아온다.
 */
public enum MemberCouponStatus {

    AVAILABLE("사용 가능"),  // 시작 상태 (DDL DEFAULT)
    USED("사용 완료");

    private final String label;

    MemberCouponStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(MemberCouponStatus next) {
        return this != next;
    }
}
