package com.cakeshop.domain.coupon.mapper;

import com.cakeshop.domain.coupon.dto.view.MemberCouponRow;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.MemberCoupon;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponMapper {

    // ==================== 쿠폰(캠페인) ====================

    int insertCoupon(Coupon coupon);

    int updateCoupon(Coupon coupon);

    Optional<Coupon> findCouponById(@Param("id") Long id);

    long countCoupons();

    List<Coupon> findCouponPage(@Param("offset") int offset, @Param("size") int size);

    /** 상태 전이는 현재 상태를 WHERE에 넣어 조건부로만 바꾼다. */
    int updateCouponStatus(@Param("id") Long id,
                           @Param("currentStatus") String currentStatus,
                           @Param("nextStatus") String nextStatus);

    /** 정원 안에서만 발급 수를 올린다. 영향 행 0 = 소진 또는 발급 불가 상태. */
    int increaseIssuedQuantity(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 발급받을 수 있는 쿠폰(ACTIVE + 기간 내). 소진 여부는 화면에서 파생값으로 판단한다. */
    List<Coupon> findIssuableCoupons(@Param("now") LocalDateTime now);

    // ==================== 회원 보유 쿠폰 ====================

    int insertMemberCoupon(MemberCoupon memberCoupon);

    boolean existsMemberCoupon(@Param("couponId") Long couponId, @Param("memberId") Long memberId);

    /** 쿠폰함 전체(만료 포함). 분류는 서비스가 파생값으로 만든다. */
    List<MemberCouponRow> findMemberCoupons(@Param("memberId") Long memberId);

    /** 지금 쓸 수 있는 쿠폰만. 최소 주문 금액 비교는 금액을 아는 서비스가 한다. */
    List<MemberCouponRow> findUsableMemberCoupons(@Param("memberId") Long memberId,
                                                  @Param("now") LocalDateTime now);

    Optional<MemberCouponRow> findMemberCouponRow(@Param("memberCouponId") Long memberCouponId,
                                                  @Param("memberId") Long memberId);

    /** AVAILABLE -> USED 조건부 UPDATE. 영향 행 1이 아니면 결제 트랜잭션을 되돌린다. */
    int markUsed(@Param("memberCouponId") Long memberCouponId,
                 @Param("memberId") Long memberId,
                 @Param("orderId") Long orderId,
                 @Param("now") LocalDateTime now);

    /** USED -> AVAILABLE 복구. 쓴 쿠폰이 없으면 0을 반환하고 취소는 그대로 진행한다. */
    int restoreByOrderId(@Param("orderId") Long orderId);

    /** 통계용 — 기간 내 사용된 쿠폰 수(statistics가 CouponService 계약으로만 쓴다). */
    long countUsedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
