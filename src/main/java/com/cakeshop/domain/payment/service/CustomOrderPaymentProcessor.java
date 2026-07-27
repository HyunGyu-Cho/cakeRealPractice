package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.coupon.dto.view.CouponDiscount;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.dto.view.CustomOrderPayableView;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.service.CustomOrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문제작 결제 링크의 DB 쪽 절반. 외부 승인 호출은 {@link CustomOrderPaymentService}가
 * 준비와 확정 사이에서 트랜잭션 밖으로 한다.
 *
 * <p>주문·링크 쪽 읽기와 쓰기는 전부 {@link CustomOrderService}를 통한다 — payment는 주문 테이블을
 * 직접 보지 않는다(절대규칙 — 도메인 격리).
 *
 * <p>중복 결제를 3중으로 막는 설계는 그대로다. 앞의 둘은 주문 도메인이,
 * 마지막 하나는 결제 도메인이 소유한다.
 * <ol>
 *   <li>{@code uk_custom_order_payment_links_quote} — 견적당 링크 1건</li>
 *   <li>링크 행 {@code FOR UPDATE} 잠금 후 {@code ISSUED} 재확인 ({@code beginLinkPayment})</li>
 *   <li>{@code payments.idempotency_key}에 토큰을 넣어 UNIQUE 충돌 시 중복 결제 차단</li>
 * </ol>
 * 주문제작 상품은 {@code stock_quantity}가 NULL(재고 미관리)이라 재고 차감이 없다.
 */
@Service
public class CustomOrderPaymentProcessor {

    private final CustomOrderService customOrderService;
    private final PaymentMapper paymentMapper;
    private final NotificationService notificationService;
    private final CouponService couponService;

    public CustomOrderPaymentProcessor(CustomOrderService customOrderService,
                                       PaymentMapper paymentMapper,
                                       NotificationService notificationService,
                                       CouponService couponService) {
        this.customOrderService = customOrderService;
        this.paymentMapper = paymentMapper;
        this.notificationService = notificationService;
        this.couponService = couponService;
    }

    /**
     * 결제 화면의 쿠폰 선택지. 원가는 견적 금액({@code links.amount})이다 —
     * 요청서 단계에는 확정 금액이 없어 쿠폰을 붙이지 않는다(스펙 6장 규칙 11).
     */
    @Transactional(readOnly = true)
    public List<AvailableCouponView> getApplicableCoupons(String token, Long memberId) {
        CustomOrderPayableView payable = customOrderService.getPayableLink(token, memberId);
        return couponService.getApplicableCoupons(memberId, payable.amount());
    }

    /**
     * 결제 화면 진입. 토큰만으로 통과시키지 않고 <b>로그인 회원이 주문 소유자인지 반드시 확인한다.</b>
     */
    @Transactional(readOnly = true)
    public CustomOrderDetailView getPayableRequest(String token, Long memberId) {
        CustomOrderPayableView payable = customOrderService.getPayableLink(token, memberId);
        return customOrderService.getMyRequest(memberId, payable.orderId());
    }

    /**
     * 결제 준비 — {@code READY} 결제 행을 만들고 승인에 쓸 주문번호·금액을 고정한다.
     * 쿠폰 선택이 바뀌어 다시 들어오면 기존 READY 행의 금액만 맞춘다.
     */
    @Transactional
    public ReadyPayment prepare(String token, Long memberId, Long memberCouponId) {
        CustomOrderPayableView payable = customOrderService.getPayableLink(token, memberId);
        long amount = payableAmount(memberId, memberCouponId, payable.amount());

        Payment existing = paymentMapper.findByIdempotencyKey(token).orElse(null);
        if (existing != null) {
            if (!PaymentStatus.READY.name().equals(existing.getStatus())) {
                throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
            }
            if (existing.getAmount() == null || existing.getAmount() != amount) {
                paymentMapper.updateReadyAmount(existing.getId(), amount);
            }
            return new ReadyPayment(existing.getId(), existing.getTossOrderId(), amount,
                payable.orderNumber());
        }

        Payment payment = new Payment();
        payment.setOrderId(payable.orderId());
        payment.setTossOrderId(payable.orderNumber());
        // 토큰을 멱등 키로 써서 같은 링크의 중복 결제가 DB에서 걸리게 한다.
        payment.setIdempotencyKey(token);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.READY.name());
        try {
            if (paymentMapper.insertPayment(payment) != 1) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
        }
        return new ReadyPayment(payment.getId(), payment.getTossOrderId(), amount,
            payable.orderNumber());
    }

    /**
     * 결제 확정 트랜잭션 — 링크 {@code USED}, 견적 금액 확정,
     * 주문 {@code UNDER_REVIEW → IN_PRODUCTION}, 결제 {@code DONE}을 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public Long confirm(String token, Long memberId, String requestedMethod, Long memberCouponId,
                        ReadyPayment ready, PaymentApproval approval) {
        String method = CheckoutPaymentProcessor.normalizeMethod(requestedMethod);
        // 링크를 잠그고 USED로 내리는 것까지가 주문 도메인의 몫이다. 같은 토큰의 동시 결제는 여기서 걸린다.
        CustomOrderPayableView payable = customOrderService.beginLinkPayment(token, memberId);

        // 쿠폰 사용도 이 트랜잭션에서 확정한다. 실패하면 링크·주문 상태까지 함께 롤백된다.
        CouponDiscount discount =
            couponService.use(memberId, memberCouponId, payable.orderId(), payable.amount());
        // 준비 시점과 확정 시점의 금액이 어긋나면 승인 금액과 주문 금액이 달라진다. 확정하지 않는다.
        if (discount.finalAmount() != approval.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        customOrderService.completeLinkPayment(payable.orderId(), payable.amount(),
            discount.discountAmount(), discount.finalAmount());

        Payment confirmed = new Payment();
        confirmed.setId(ready.paymentId());
        confirmed.setOrderId(payable.orderId());
        confirmed.setPaymentKey(approval.paymentKey());
        confirmed.setMethod(method);
        confirmed.setAmount(approval.amount());
        confirmed.setProviderStatus(approval.providerStatus());
        confirmed.setApprovedAt(approval.approvedAt());
        if (paymentMapper.confirmPayment(confirmed) != 1) {
            // status='READY' 조건이 걸렀다 — 같은 토큰으로 이미 결제됐다는 뜻이다.
            throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
        }

        notifyPaid(payable);
        return payable.orderId();
    }

    /** 결제창 실패·이탈로 준비된 결제를 마감한다. 링크는 {@code ISSUED}로 남아 다시 결제할 수 있다. */
    @Transactional
    public void markFailed(String token, String code, String message) {
        paymentMapper.findByIdempotencyKey(token).ifPresent(payment ->
            paymentMapper.abortPayment(payment.getId(), PaymentStatus.ABORTED.name(),
                code, code, message));
    }

    /** 쿠폰을 반영한 실제 결제 금액. 쿠폰이 목록에서 사라졌으면 정가로 진행한다. */
    private long payableAmount(Long memberId, Long memberCouponId, long linkAmount) {
        if (memberCouponId == null) {
            return linkAmount;
        }
        return couponService.getApplicableCoupons(memberId, linkAmount).stream()
            .filter(coupon -> coupon.memberCouponId().equals(memberCouponId))
            .findFirst()
            .map(AvailableCouponView::discountAmount)
            .map(discount -> linkAmount - discount)
            .orElse(linkAmount);
    }

    private void notifyPaid(CustomOrderPayableView payable) {
        notificationService.notify(NotificationCommand.forOrder(
            payable.memberId(), NotificationType.ORDER_PAID, payable.orderId(),
            NotificationType.ORDER_PAID.label(),
            "주문제작 " + payable.orderNumber() + " 결제가 완료되어 제작을 시작합니다."));
        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_ORDER_PLACED,
            NotificationType.ADMIN_ORDER_PLACED.label(),
            "주문제작 " + payable.orderNumber() + " 결제가 완료되었습니다.",
            "/admin/custom-orders/" + payable.orderId(), payable.orderId(), null));
    }

    /** 준비된 결제. 승인 호출에 필요한 값이다. */
    public record ReadyPayment(Long paymentId, String tossOrderId, long amount, String orderNumber) {
    }
}
