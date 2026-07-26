package com.cakeshop.domain.coupon.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원이 보유한 쿠폰 1장.
 *
 * <p>{@code appliedOrderId}는 order 도메인의 값을 담지만 <b>이 테이블의 컬럼</b>이라 값만 저장한다.
 * 주문 정보가 필요하면 {@code OrderService}의 공개 계약으로 조회한다(orders JOIN 금지).
 */
@Getter
@Setter
public class MemberCoupon {
    private Long id;
    private Long couponId;
    private Long memberId;
    private String status;
    private Long appliedOrderId;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
}
