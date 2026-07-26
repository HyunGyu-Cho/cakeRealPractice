package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.coupon.dto.view.CouponDiscount;
import com.cakeshop.domain.coupon.dto.view.DownloadableCouponView;
import com.cakeshop.domain.coupon.dto.view.MemberCouponRow;
import com.cakeshop.domain.coupon.dto.view.MyCouponView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.entity.MemberCoupon;
import com.cakeshop.domain.coupon.entity.MemberCouponStatus;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 쿠폰 흐름 — 다운로드, 쿠폰함, 결제 시 적용·사용 확정·복구.
 *
 * <p>order·payment 도메인은 <b>이 클래스의 공개 메서드로만</b> 쿠폰에 접근한다
 * (스펙 docs/specs/coupon.md 4장). 반대로 coupon은 orders·members를 조회하지 않는다.
 *
 * <p>쿠폰을 체크아웃 화면에서 미리 잡아두지 않는 이유는, 결제까지 가지 않은 초안이 쿠폰을 계속
 * 묶어버리기 때문이다. 사용 확정은 결제 트랜잭션 안에서 조건부 UPDATE 한 번으로만 한다.
 */
@Service
public class CouponService {

    private final CouponMapper couponMapper;
    private final Clock clock;

    @Autowired
    public CouponService(CouponMapper couponMapper) {
        this(couponMapper, Clock.systemDefaultZone());
    }

    public CouponService(CouponMapper couponMapper, Clock clock) {
        this.couponMapper = couponMapper;
        this.clock = clock;
    }

    // ==================== 조회 ====================

    /** 쿠폰함. 사용 가능 / 사용 완료 / 기간 만료 분류는 저장값이 아니라 파생값이다. */
    @Transactional(readOnly = true)
    public List<MyCouponView> getMyCoupons(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return couponMapper.findMemberCoupons(memberId).stream()
            .map(row -> toMyCouponView(row, now))
            .toList();
    }

    /** 다운로드 가능 쿠폰 목록. 이미 받은 쿠폰도 "받음"으로 함께 보여준다. */
    @Transactional(readOnly = true)
    public List<DownloadableCouponView> getDownloadableCoupons(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return couponMapper.findIssuableCoupons(now).stream()
            .map(coupon -> {
                boolean owned = couponMapper.existsMemberCoupon(coupon.getId(), memberId);
                int remaining = remainingQuantity(coupon);
                String reason = owned ? "이미 받은 쿠폰입니다."
                    : remaining <= 0 ? "모두 소진되었습니다." : null;
                return new DownloadableCouponView(
                    coupon.getId(), coupon.getName(), describe(coupon),
                    coupon.getMinimumOrderAmount(), coupon.getExpiresAt(),
                    remaining, owned, reason == null, reason);
            })
            .toList();
    }

    /** 이 금액에 지금 쓸 수 있는 쿠폰과 각각의 할인액. 최소 주문 금액에 못 미치는 쿠폰은 빠진다. */
    @Transactional(readOnly = true)
    public List<AvailableCouponView> getApplicableCoupons(Long memberId, long originalAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        return couponMapper.findUsableMemberCoupons(memberId, now).stream()
            .filter(row -> originalAmount >= row.minimumOrderAmount())
            .map(row -> new AvailableCouponView(
                row.memberCouponId(), row.couponName(), describe(row),
                discountOf(row, originalAmount), row.minimumOrderAmount(), row.expiresAt()))
            .toList();
    }

    // ==================== 적용 ====================

    /**
     * 화면·결제가 쓰는 금액 계산. {@code memberCouponId}가 null이면 할인 없음이 정상 경로다.
     * 클라이언트가 보낸 할인액은 받지 않는다 — 원가만 받고 서버가 다시 계산한다.
     */
    @Transactional(readOnly = true)
    public CouponDiscount calculateDiscount(Long memberId, Long memberCouponId, long originalAmount) {
        if (memberCouponId == null) {
            return CouponDiscount.none(originalAmount);
        }
        MemberCouponRow row = requireUsable(memberId, memberCouponId, originalAmount);
        long discount = discountOf(row, originalAmount);
        return new CouponDiscount(memberCouponId, discount, originalAmount - discount);
    }

    /**
     * 사용 확정. <b>결제 트랜잭션 안에서만</b> 호출한다.
     * 조건부 UPDATE 영향 행이 1이 아니면 예외를 던져 결제 전체를 롤백시킨다.
     */
    @Transactional
    public CouponDiscount use(Long memberId, Long memberCouponId, Long orderId, long originalAmount) {
        if (memberCouponId == null) {
            return CouponDiscount.none(originalAmount);
        }
        MemberCouponRow row = requireUsable(memberId, memberCouponId, originalAmount);
        try {
            if (couponMapper.markUsed(memberCouponId, memberId, orderId, LocalDateTime.now(clock)) != 1) {
                throw new BusinessException(CouponErrorCode.USE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            // uk_member_coupons_applied_order — 같은 주문에 쿠폰이 이미 붙어 있다.
            throw new BusinessException(CouponErrorCode.USE_FAILED);
        }
        long discount = discountOf(row, originalAmount);
        return new CouponDiscount(memberCouponId, discount, originalAmount - discount);
    }

    /**
     * 주문 취소 시 복구. <b>취소 트랜잭션 안에서</b> 호출한다.
     * 쓴 쿠폰이 없으면 조용히 통과한다(쿠폰 없는 주문이 대부분이다).
     * 기간이 이미 지난 쿠폰도 되돌려 놓는다 — 파생 만료라 자연히 다시 쓰이지 않는다.
     */
    @Transactional
    public void restoreByOrderId(Long orderId) {
        couponMapper.restoreByOrderId(orderId);
    }

    /**
     * [공개 계약] 기간 내 쿠폰 사용 건수. statistics 통계 화면이 첫 사용처다.
     * 주문 취소로 복구된 쿠폰은 used_at이 지워지므로 자연히 제외된다.
     */
    @Transactional(readOnly = true)
    public long countUsedCoupons(LocalDate from, LocalDate to) {
        return couponMapper.countUsedBetween(
            from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    // ==================== 다운로드 ====================

    /** 고객 자가 다운로드. 정원은 조건부 UPDATE로, 1인 1장은 UNIQUE로 막는다. */
    @Transactional
    public void download(Long memberId, Long couponId) {
        if (couponMapper.existsMemberCoupon(couponId, memberId)) {
            throw new BusinessException(CouponErrorCode.ALREADY_DOWNLOADED);
        }
        issue(memberId, couponId);
    }

    /**
     * 발급 1건. 고객 다운로드와 관리자 지정 발급이 같은 규칙을 타도록 여기 모았다.
     * 수량 증가를 먼저 하는 이유는, 발급 행부터 넣으면 정원 초과를 확인한 뒤 되돌려야 하기 때문이다.
     */
    @Transactional
    public void issue(Long memberId, Long couponId) {
        couponMapper.findCouponById(couponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.NOT_FOUND));
        if (couponMapper.increaseIssuedQuantity(couponId, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(CouponErrorCode.SOLD_OUT);
        }
        MemberCoupon memberCoupon = new MemberCoupon();
        memberCoupon.setCouponId(couponId);
        memberCoupon.setMemberId(memberId);
        memberCoupon.setStatus(MemberCouponStatus.AVAILABLE.name());
        try {
            couponMapper.insertMemberCoupon(memberCoupon);
        } catch (DuplicateKeyException exception) {
            // uk_member_coupons_coupon_member — 동시 요청이 앱 확인을 통과한 경우
            throw new BusinessException(CouponErrorCode.ALREADY_DOWNLOADED);
        }
    }

    // ==================== 내부 헬퍼 ====================

    /** 유효 판정 3종(쿠폰 상태·기간·보유 상태) + 최소 주문 금액. 조회와 사용 확정 양쪽에서 쓴다. */
    private MemberCouponRow requireUsable(Long memberId, Long memberCouponId, long originalAmount) {
        MemberCouponRow row = couponMapper.findMemberCouponRow(memberCouponId, memberId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.NOT_OWNED));
        if (MemberCouponStatus.USED.name().equals(row.status())) {
            throw new BusinessException(CouponErrorCode.ALREADY_USED);
        }
        if (!CouponStatus.ACTIVE.name().equals(row.couponStatus())) {
            throw new BusinessException(CouponErrorCode.NOT_ISSUABLE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(row.startsAt()) || !now.isBefore(row.expiresAt())) {
            throw new BusinessException(CouponErrorCode.EXPIRED_COUPON);
        }
        if (originalAmount < row.minimumOrderAmount()) {
            throw new BusinessException(CouponErrorCode.MINIMUM_ORDER_AMOUNT);
        }
        return row;
    }

    private long discountOf(MemberCouponRow row, long originalAmount) {
        return DiscountType.valueOf(row.discountType())
            .calculate(originalAmount, row.discountValue(), row.maximumDiscountAmount());
    }

    private MyCouponView toMyCouponView(MemberCouponRow row, LocalDateTime now) {
        boolean used = MemberCouponStatus.USED.name().equals(row.status());
        boolean unusable = !now.isBefore(row.expiresAt())
            || !CouponStatus.ACTIVE.name().equals(row.couponStatus());
        String state = used ? MyCouponView.USED
            : unusable ? MyCouponView.EXPIRED
            : MyCouponView.AVAILABLE;
        String label = switch (state) {
            case MyCouponView.USED -> "사용 완료";
            case MyCouponView.EXPIRED -> "기간 만료";
            default -> "사용 가능";
        };
        return new MyCouponView(row.memberCouponId(), row.couponName(), describe(row),
            row.minimumOrderAmount(), row.expiresAt(), row.usedAt(), state, label);
    }

    private String describe(MemberCouponRow row) {
        return DiscountType.valueOf(row.discountType())
            .describe(row.discountValue(), row.maximumDiscountAmount());
    }

    private String describe(Coupon coupon) {
        return DiscountType.valueOf(coupon.getDiscountType())
            .describe(coupon.getDiscountValue(), coupon.getMaximumDiscountAmount());
    }

    private int remainingQuantity(Coupon coupon) {
        return Math.max(0, coupon.getTotalQuantity() - coupon.getIssuedQuantity());
    }
}
