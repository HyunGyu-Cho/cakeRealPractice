package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.coupon.dto.view.CouponDiscount;
import com.cakeshop.domain.coupon.dto.view.MemberCouponRow;
import com.cakeshop.domain.coupon.dto.view.MyCouponView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.MemberCoupon;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class CouponServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Long MEMBER_ID = 7L;

    @Mock private CouponMapper couponMapper;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        couponService = new CouponService(couponMapper, clock);
    }

    // ==================== 할인액 계산 ====================

    @Test
    void percentageDiscountIsCappedByMaximumDiscountAmount() {
        givenOwnedCoupon(row("PERCENTAGE", 10L, 0L, 5_000L));

        CouponDiscount discount = couponService.calculateDiscount(MEMBER_ID, 1L, 100_000L);

        // 10%면 10,000원이지만 최대 할인 5,000원이 상한이다
        assertThat(discount.discountAmount()).isEqualTo(5_000L);
        assertThat(discount.finalAmount()).isEqualTo(95_000L);
    }

    @Test
    void fixedDiscountNeverExceedsTheOriginalAmount() {
        givenOwnedCoupon(row("FIXED_AMOUNT", 30_000L, 0L, null));

        CouponDiscount discount = couponService.calculateDiscount(MEMBER_ID, 1L, 20_000L);

        // 최종 결제 금액이 음수가 되면 안 된다
        assertThat(discount.discountAmount()).isEqualTo(20_000L);
        assertThat(discount.finalAmount()).isZero();
    }

    @Test
    void noCouponSelectedIsANormalPath() {
        CouponDiscount discount = couponService.calculateDiscount(MEMBER_ID, null, 20_000L);

        assertThat(discount.applied()).isFalse();
        assertThat(discount.finalAmount()).isEqualTo(20_000L);
    }

    // ==================== 유효 판정 ====================

    @Test
    void rejectsWhenMinimumOrderAmountIsNotMet() {
        givenOwnedCoupon(row("FIXED_AMOUNT", 3_000L, 15_000L, null));

        assertThatThrownBy(() -> couponService.calculateDiscount(MEMBER_ID, 1L, 14_999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.MINIMUM_ORDER_AMOUNT);
    }

    @Test
    void rejectsExpiredCouponAtTheBoundary() {
        MemberCouponRow expired = new MemberCouponRow(1L, 2L, "만료 쿠폰", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(10), NOW, "ACTIVE", "AVAILABLE", null, NOW.minusDays(10), null);
        givenOwnedCoupon(expired);

        // 만료 시각은 포함하지 않는다(expires_at 경계부터 사용 불가)
        assertThatThrownBy(() -> couponService.calculateDiscount(MEMBER_ID, 1L, 50_000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.EXPIRED_COUPON);
    }

    @Test
    void rejectsCouponOfAnEndedCampaign() {
        MemberCouponRow ended = new MemberCouponRow(1L, 2L, "종료 쿠폰", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(1), NOW.plusDays(1), "ENDED", "AVAILABLE", null, NOW, null);
        givenOwnedCoupon(ended);

        // 종료한 쿠폰은 이미 발급된 것도 못 쓴다. 발급 행을 일괄 갱신하지 않는 이유다.
        assertThatThrownBy(() -> couponService.calculateDiscount(MEMBER_ID, 1L, 50_000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.NOT_ISSUABLE);
    }

    @Test
    void rejectsCouponOwnedByAnotherMember() {
        when(couponMapper.findMemberCouponRow(1L, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.calculateDiscount(MEMBER_ID, 1L, 50_000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.NOT_OWNED);
    }

    @Test
    void rejectsAlreadyUsedCoupon() {
        MemberCouponRow used = new MemberCouponRow(1L, 2L, "사용 완료", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(1), NOW.plusDays(1), "ACTIVE", "USED", 55L, NOW, NOW);
        givenOwnedCoupon(used);

        assertThatThrownBy(() -> couponService.calculateDiscount(MEMBER_ID, 1L, 50_000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.ALREADY_USED);
    }

    // ==================== 사용 확정·복구 ====================

    @Test
    void useMarksCouponUsedWithConditionalUpdate() {
        givenOwnedCoupon(row("FIXED_AMOUNT", 3_000L, 0L, null));
        when(couponMapper.markUsed(1L, MEMBER_ID, 55L, NOW)).thenReturn(1);

        CouponDiscount discount = couponService.use(MEMBER_ID, 1L, 55L, 50_000L);

        assertThat(discount.discountAmount()).isEqualTo(3_000L);
        assertThat(discount.finalAmount()).isEqualTo(47_000L);
        verify(couponMapper).markUsed(1L, MEMBER_ID, 55L, NOW);
    }

    @Test
    void useFailsWhenAnotherRequestConsumedTheCouponFirst() {
        givenOwnedCoupon(row("FIXED_AMOUNT", 3_000L, 0L, null));
        // 조회와 UPDATE 사이에 다른 결제가 먼저 썼다 — 영향 행 0
        when(couponMapper.markUsed(1L, MEMBER_ID, 55L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> couponService.use(MEMBER_ID, 1L, 55L, 50_000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.USE_FAILED);
    }

    @Test
    void useWithoutCouponDoesNotTouchTheMapper() {
        CouponDiscount discount = couponService.use(MEMBER_ID, null, 55L, 50_000L);

        assertThat(discount.applied()).isFalse();
        verify(couponMapper, never()).markUsed(any(), any(), any(), any());
    }

    @Test
    void restoreDelegatesToConditionalUpdateByOrder() {
        couponService.restoreByOrderId(55L);

        verify(couponMapper).restoreByOrderId(55L);
    }

    // ==================== 발급 ====================

    @Test
    void downloadIncreasesIssuedQuantityThenInsertsMemberCoupon() {
        when(couponMapper.existsMemberCoupon(2L, MEMBER_ID)).thenReturn(false);
        when(couponMapper.findCouponById(2L)).thenReturn(Optional.of(coupon()));
        when(couponMapper.increaseIssuedQuantity(2L, NOW)).thenReturn(1);

        couponService.download(MEMBER_ID, 2L);

        verify(couponMapper).increaseIssuedQuantity(2L, NOW);
        verify(couponMapper).insertMemberCoupon(any(MemberCoupon.class));
    }

    @Test
    void downloadRejectsWhenQuantityIsExhausted() {
        when(couponMapper.existsMemberCoupon(2L, MEMBER_ID)).thenReturn(false);
        when(couponMapper.findCouponById(2L)).thenReturn(Optional.of(coupon()));
        // 정원 초과는 앱이 아니라 조건부 UPDATE가 막는다
        when(couponMapper.increaseIssuedQuantity(2L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> couponService.download(MEMBER_ID, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.SOLD_OUT);
        verify(couponMapper, never()).insertMemberCoupon(any());
    }

    @Test
    void downloadRejectsSecondAttemptBySameMember() {
        when(couponMapper.existsMemberCoupon(2L, MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> couponService.download(MEMBER_ID, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.ALREADY_DOWNLOADED);
    }

    @Test
    void concurrentDownloadIsStoppedByTheUniqueConstraint() {
        when(couponMapper.existsMemberCoupon(2L, MEMBER_ID)).thenReturn(false);
        when(couponMapper.findCouponById(2L)).thenReturn(Optional.of(coupon()));
        when(couponMapper.increaseIssuedQuantity(2L, NOW)).thenReturn(1);
        // 앱 확인을 통과한 동시 요청은 uk_member_coupons_coupon_member에서 걸린다
        when(couponMapper.insertMemberCoupon(any(MemberCoupon.class)))
            .thenThrow(new DuplicateKeyException("uk_member_coupons_coupon_member"));

        assertThatThrownBy(() -> couponService.download(MEMBER_ID, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.ALREADY_DOWNLOADED);
    }

    // ==================== 조회 파생값 ====================

    @Test
    void myCouponsAreClassifiedByStatusAndExpiryDerivation() {
        MemberCouponRow available = new MemberCouponRow(1L, 2L, "사용 가능", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(1), NOW.plusDays(1), "ACTIVE", "AVAILABLE", null, NOW, null);
        MemberCouponRow used = new MemberCouponRow(2L, 2L, "사용 완료", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(1), NOW.plusDays(1), "ACTIVE", "USED", 55L, NOW, NOW);
        MemberCouponRow expired = new MemberCouponRow(3L, 2L, "기간 만료", "FIXED_AMOUNT", 3_000L,
            0L, null, NOW.minusDays(10), NOW.minusDays(1), "ACTIVE", "AVAILABLE", null, NOW, null);
        when(couponMapper.findMemberCoupons(MEMBER_ID)).thenReturn(List.of(available, used, expired));

        List<MyCouponView> coupons = couponService.getMyCoupons(MEMBER_ID);

        assertThat(coupons).extracting(MyCouponView::state)
            .containsExactly(MyCouponView.AVAILABLE, MyCouponView.USED, MyCouponView.EXPIRED);
    }

    @Test
    void applicableCouponsExcludeThoseBelowTheMinimumOrderAmount() {
        MemberCouponRow small = new MemberCouponRow(1L, 2L, "3천원", "FIXED_AMOUNT", 3_000L,
            15_000L, null, NOW.minusDays(1), NOW.plusDays(1), "ACTIVE", "AVAILABLE", null, NOW, null);
        MemberCouponRow big = new MemberCouponRow(2L, 3L, "1만원", "FIXED_AMOUNT", 10_000L,
            100_000L, null, NOW.minusDays(1), NOW.plusDays(1), "ACTIVE", "AVAILABLE", null, NOW, null);
        when(couponMapper.findUsableMemberCoupons(MEMBER_ID, NOW)).thenReturn(List.of(small, big));

        List<AvailableCouponView> coupons = couponService.getApplicableCoupons(MEMBER_ID, 20_000L);

        assertThat(coupons).extracting(AvailableCouponView::memberCouponId).containsExactly(1L);
        assertThat(coupons.getFirst().discountAmount()).isEqualTo(3_000L);
    }

    private void givenOwnedCoupon(MemberCouponRow row) {
        when(couponMapper.findMemberCouponRow(1L, MEMBER_ID)).thenReturn(Optional.of(row));
    }

    private MemberCouponRow row(String discountType, long discountValue,
                                long minimumOrderAmount, Long maximumDiscountAmount) {
        return new MemberCouponRow(1L, 2L, "테스트 쿠폰", discountType, discountValue,
            minimumOrderAmount, maximumDiscountAmount, NOW.minusDays(1), NOW.plusDays(30),
            "ACTIVE", "AVAILABLE", null, NOW.minusDays(1), null);
    }

    private Coupon coupon() {
        Coupon coupon = new Coupon();
        coupon.setId(2L);
        coupon.setName("테스트 쿠폰");
        coupon.setDiscountType("FIXED_AMOUNT");
        coupon.setDiscountValue(3_000L);
        coupon.setMinimumOrderAmount(0L);
        coupon.setTotalQuantity(100);
        coupon.setIssuedQuantity(0);
        coupon.setStartsAt(NOW.minusDays(1));
        coupon.setExpiresAt(NOW.plusDays(30));
        coupon.setStatus("ACTIVE");
        return coupon;
    }
}
