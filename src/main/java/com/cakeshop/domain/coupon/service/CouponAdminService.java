package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.form.CouponForm;
import com.cakeshop.domain.coupon.dto.view.CouponAdminListView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 쿠폰 관리 — 등록·수정·상태 전이·지정 발급. */
@Service
public class CouponAdminService {

    private final CouponMapper couponMapper;
    private final CouponService couponService;
    private final MemberService memberService;
    private final Clock clock;

    @Autowired
    public CouponAdminService(CouponMapper couponMapper, CouponService couponService,
                              MemberService memberService) {
        this(couponMapper, couponService, memberService, Clock.systemDefaultZone());
    }

    public CouponAdminService(CouponMapper couponMapper, CouponService couponService,
                              MemberService memberService, Clock clock) {
        this.couponMapper = couponMapper;
        this.couponService = couponService;
        this.memberService = memberService;
        this.clock = clock;
    }

    /**
     * 지정 발급 대상 후보. members 테이블을 직접 읽지 않고 member 도메인의 공개 계약만 쓴다.
     * 키워드가 없으면 목록을 만들지 않는다(전 회원을 훑을 이유가 없다).
     */
    @Transactional(readOnly = true)
    public List<MemberProfileView> searchIssueTargets(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<Long> memberIds = memberService.searchMemberIds(keyword.trim());
        return List.copyOf(memberService.getProfileMap(memberIds).values());
    }

    @Transactional(readOnly = true)
    public PageResult<CouponAdminListView> getCouponPage(PageRequest pageRequest) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CouponAdminListView> content =
            couponMapper.findCouponPage(pageRequest.getOffset(), pageRequest.getSize()).stream()
                .map(coupon -> toListView(coupon, now))
                .toList();
        return new PageResult<>(content, pageRequest, couponMapper.countCoupons());
    }

    @Transactional(readOnly = true)
    public CouponForm getCouponForm(Long couponId) {
        return CouponForm.from(findCoupon(couponId));
    }

    @Transactional(readOnly = true)
    public CouponAdminListView getCoupon(Long couponId) {
        return toListView(findCoupon(couponId), LocalDateTime.now(clock));
    }

    @Transactional
    public Long createCoupon(CouponForm form, Long adminId) {
        Coupon coupon = new Coupon();
        apply(coupon, form);
        coupon.setStatus(CouponStatus.ACTIVE.name());
        coupon.setCreatedBy(adminId);
        if (couponMapper.insertCoupon(coupon) != 1 || coupon.getId() == null) {
            throw new BusinessException(CouponErrorCode.NOT_FOUND);
        }
        return coupon.getId();
    }

    /** 종료된 쿠폰은 수정하지 않는다. 이미 발급된 쿠폰의 조건이 뒤늦게 바뀌는 것을 막는다. */
    @Transactional
    public void updateCoupon(Long couponId, CouponForm form) {
        Coupon coupon = findCoupon(couponId);
        if (CouponStatus.ENDED.name().equals(coupon.getStatus())) {
            throw new BusinessException(CouponErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (form.getTotalQuantity() < coupon.getIssuedQuantity()) {
            throw new BusinessException(CouponErrorCode.SOLD_OUT);
        }
        apply(coupon, form);
        coupon.setId(couponId);
        couponMapper.updateCoupon(coupon);
    }

    /** 상태 전이는 {@link CouponStatus}가 소유하고, DB에는 현재 상태를 건 조건부 UPDATE로만 반영한다. */
    @Transactional
    public void changeStatus(Long couponId, CouponStatus next) {
        Coupon coupon = findCoupon(couponId);
        CouponStatus current = CouponStatus.valueOf(coupon.getStatus());
        if (!current.canTransitionTo(next)
            || couponMapper.updateCouponStatus(couponId, current.name(), next.name()) != 1) {
            throw new BusinessException(CouponErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /**
     * 지정 발급. 고객 다운로드와 같은 정원·1인 1장 규칙을 탄다.
     * 발급 경로만 다르고 규칙은 {@link CouponService#issue}가 단독으로 소유한다.
     */
    @Transactional
    public void issueToMember(Long couponId, Long memberId) {
        if (couponMapper.existsMemberCoupon(couponId, memberId)) {
            throw new BusinessException(CouponErrorCode.ALREADY_DOWNLOADED);
        }
        couponService.issue(memberId, couponId);
    }

    private Coupon findCoupon(Long couponId) {
        return couponMapper.findCouponById(couponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.NOT_FOUND));
    }

    private void apply(Coupon coupon, CouponForm form) {
        coupon.setName(form.getName());
        coupon.setDiscountType(form.getDiscountType().name());
        coupon.setDiscountValue(form.getDiscountValue());
        coupon.setMinimumOrderAmount(form.getMinimumOrderAmount());
        coupon.setMaximumDiscountAmount(form.getMaximumDiscountAmount());
        coupon.setTotalQuantity(form.getTotalQuantity());
        coupon.setStartsAt(form.getStartsAt());
        coupon.setExpiresAt(form.getExpiresAt());
    }

    private CouponAdminListView toListView(Coupon coupon, LocalDateTime now) {
        DiscountType discountType = DiscountType.valueOf(coupon.getDiscountType());
        CouponStatus status = CouponStatus.valueOf(coupon.getStatus());
        return new CouponAdminListView(
            coupon.getId(), coupon.getName(),
            discountType.name(), discountType.label(), coupon.getDiscountValue(),
            discountType.describe(coupon.getDiscountValue(), coupon.getMaximumDiscountAmount()),
            coupon.getMinimumOrderAmount(), coupon.getMaximumDiscountAmount(),
            coupon.getTotalQuantity(), coupon.getIssuedQuantity(),
            coupon.getStartsAt(), coupon.getExpiresAt(),
            status.name(), status.label(),
            !now.isBefore(coupon.getExpiresAt()),
            coupon.getIssuedQuantity() >= coupon.getTotalQuantity());
    }
}
