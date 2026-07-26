package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.form.CouponForm;
import com.cakeshop.domain.coupon.dto.view.CouponAdminListView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock private CouponMapper couponMapper;
    @Mock private CouponService couponService;
    @Mock private MemberService memberService;

    private CouponAdminService couponAdminService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        couponAdminService = new CouponAdminService(couponMapper, couponService, memberService, clock);
    }

    @Test
    void createStartsAsActiveAndRecordsTheAdmin() {
        when(couponMapper.insertCoupon(any(Coupon.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Coupon.class).setId(11L);
            return 1;
        });

        Long id = couponAdminService.createCoupon(form(DiscountType.PERCENTAGE, 10L), 3L);

        assertThat(id).isEqualTo(11L);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insertCoupon(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CouponStatus.ACTIVE.name());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(3L);
    }

    @Test
    void statusTransitionIsOwnedByTheEnum() {
        when(couponMapper.findCouponById(11L)).thenReturn(Optional.of(coupon("ENDED", 0)));

        // ENDED 는 최종 상태라 되돌릴 수 없다
        assertThatThrownBy(() -> couponAdminService.changeStatus(11L, CouponStatus.ACTIVE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_STATUS_TRANSITION);
        verify(couponMapper, never()).updateCouponStatus(any(), any(), any());
    }

    @Test
    void suspendUsesConditionalUpdateWithTheCurrentStatus() {
        when(couponMapper.findCouponById(11L)).thenReturn(Optional.of(coupon("ACTIVE", 0)));
        when(couponMapper.updateCouponStatus(11L, "ACTIVE", "SUSPENDED")).thenReturn(1);

        couponAdminService.changeStatus(11L, CouponStatus.SUSPENDED);

        verify(couponMapper).updateCouponStatus(11L, "ACTIVE", "SUSPENDED");
    }

    @Test
    void updateRejectsEndedCoupon() {
        when(couponMapper.findCouponById(11L)).thenReturn(Optional.of(coupon("ENDED", 0)));

        assertThatThrownBy(() ->
            couponAdminService.updateCoupon(11L, form(DiscountType.FIXED_AMOUNT, 3_000L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void updateRejectsQuantityBelowWhatWasAlreadyIssued() {
        when(couponMapper.findCouponById(11L)).thenReturn(Optional.of(coupon("ACTIVE", 40)));
        CouponForm form = form(DiscountType.FIXED_AMOUNT, 3_000L);
        form.setTotalQuantity(30);

        assertThatThrownBy(() -> couponAdminService.updateCoupon(11L, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.SOLD_OUT);
        verify(couponMapper, never()).updateCoupon(any());
    }

    @Test
    void issueToMemberReusesTheCustomerDownloadRules() {
        when(couponMapper.existsMemberCoupon(11L, 5L)).thenReturn(false);

        couponAdminService.issueToMember(11L, 5L);

        // 정원·1인 1장 규칙은 CouponService.issue 가 단독으로 소유한다
        verify(couponService).issue(5L, 11L);
    }

    @Test
    void issueToMemberRejectsDuplicate() {
        when(couponMapper.existsMemberCoupon(11L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> couponAdminService.issueToMember(11L, 5L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.ALREADY_DOWNLOADED);
        verify(couponService, never()).issue(any(), any());
    }

    @Test
    void listViewDerivesExpiredAndSoldOutInsteadOfStoringThem() {
        Coupon coupon = coupon("ACTIVE", 100);
        coupon.setExpiresAt(NOW.minusDays(1));
        when(couponMapper.findCouponPage(0, 10)).thenReturn(List.of(coupon));
        when(couponMapper.countCoupons()).thenReturn(1L);

        PageResult<CouponAdminListView> page =
            couponAdminService.getCouponPage(new PageRequest(1, 10));

        CouponAdminListView view = page.getContent().getFirst();
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.expired()).isTrue();
        assertThat(view.soldOut()).isTrue();
    }

    @Test
    void issueTargetSearchSkipsWhenKeywordIsBlank() {
        assertThat(couponAdminService.searchIssueTargets("  ")).isEmpty();
        verify(memberService, never()).searchMemberIds(any());
    }

    private CouponForm form(DiscountType type, long value) {
        CouponForm form = new CouponForm();
        form.setName("테스트 쿠폰");
        form.setDiscountType(type);
        form.setDiscountValue(value);
        form.setMinimumOrderAmount(0L);
        form.setTotalQuantity(100);
        form.setStartsAt(NOW.minusDays(1));
        form.setExpiresAt(NOW.plusDays(30));
        return form;
    }

    private Coupon coupon(String status, int issuedQuantity) {
        Coupon coupon = new Coupon();
        coupon.setId(11L);
        coupon.setName("테스트 쿠폰");
        coupon.setDiscountType("FIXED_AMOUNT");
        coupon.setDiscountValue(3_000L);
        coupon.setMinimumOrderAmount(0L);
        coupon.setTotalQuantity(100);
        coupon.setIssuedQuantity(issuedQuantity);
        coupon.setStartsAt(NOW.minusDays(1));
        coupon.setExpiresAt(NOW.plusDays(30));
        coupon.setStatus(status);
        return coupon;
    }
}
