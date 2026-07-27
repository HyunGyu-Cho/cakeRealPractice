package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.payment.dto.view.PaymentPrepareView;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.payment.service.CustomOrderPaymentProcessor.ReadyPayment;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 주문제작 결제 링크 결제의 경계. 일반 결제와 같은 준비 → 승인 → 확정 2단계를 탄다.
 *
 * <p>외부 승인·보상 취소 호출은 전부 여기서, DB 트랜잭션 밖에서 한다
 * (스펙 docs/specs/order-payment.md 결제 흐름).
 */
@Service
public class CustomOrderPaymentService {
    private static final Logger log = LoggerFactory.getLogger(CustomOrderPaymentService.class);

    private final CustomOrderPaymentProcessor processor;
    private final PaymentMapper paymentMapper;
    private final PaymentGateway gateway;

    public CustomOrderPaymentService(CustomOrderPaymentProcessor processor,
                                     PaymentMapper paymentMapper, PaymentGateway gateway) {
        this.processor = processor;
        this.paymentMapper = paymentMapper;
        this.gateway = gateway;
    }

    public List<AvailableCouponView> getApplicableCoupons(String token, Long memberId) {
        return processor.getApplicableCoupons(token, memberId);
    }

    public CustomOrderDetailView getPayableRequest(String token, Long memberId) {
        return processor.getPayableRequest(token, memberId);
    }

    /** 결제 화면이 결제를 시작하는 데 필요한 값. 금액은 서버가 쿠폰까지 반영해 고정한다. */
    public PaymentPrepareView prepare(String token, Long memberId, Long memberCouponId) {
        ReadyPayment ready = processor.prepare(token, memberId, memberCouponId);
        return new PaymentPrepareView(gateway.provider(), gateway.clientKey(),
            ready.tossOrderId(), ready.amount(), "주문제작 " + ready.orderNumber());
    }

    /** 모의 결제 경로 — 결제창이 없어 폼 제출이 곧 승인 요청이다. */
    public Long pay(String token, Long memberId, String method, Long memberCouponId) {
        ReadyPayment ready = processor.prepare(token, memberId, memberCouponId);
        return approveAndConfirm(token, memberId, method, memberCouponId, ready, null);
    }

    /** 실결제 콜백 경로 — 콜백이 돌려준 주문번호·금액을 저장된 값과 대조한 뒤 승인한다. */
    public Long confirm(String token, Long memberId, String paymentKey, String tossOrderId,
                        long amount, String method, Long memberCouponId) {
        ReadyPayment ready = paymentMapper.findByIdempotencyKey(token)
            .filter(payment -> PaymentStatus.READY.name().equals(payment.getStatus()))
            .map(payment -> new ReadyPayment(payment.getId(), payment.getTossOrderId(),
                payment.getAmount(), payment.getTossOrderId()))
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.ALREADY_PAID));

        if (!ready.tossOrderId().equals(tossOrderId) || ready.amount() != amount) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        return approveAndConfirm(token, memberId, method, memberCouponId, ready, paymentKey);
    }

    /** 결제창 실패·이탈. 준비된 결제만 마감하고 링크는 살려 둔다. */
    public void markFailed(String token, String code, String message) {
        processor.markFailed(token, code, message);
    }

    private Long approveAndConfirm(String token, Long memberId, String method, Long memberCouponId,
                                   ReadyPayment ready, String paymentKey) {
        PaymentApproval approval =
            gateway.approve(paymentKey, ready.tossOrderId(), ready.amount(), token);
        try {
            return processor.confirm(token, memberId, method, memberCouponId, ready, approval);
        } catch (RuntimeException e) {
            compensate(token, ready, approval, e);
            throw e instanceof BusinessException business ? business
                : new BusinessException(PaymentErrorCode.CONFIRM_FAILED, e);
        }
    }

    /** 승인은 됐는데 확정이 실패했다. 돈이 남지 않도록 즉시 되돌린다. */
    private void compensate(String token, ReadyPayment ready, PaymentApproval approval,
                            RuntimeException cause) {
        try {
            gateway.cancel(approval.paymentKey(), "주문제작 결제 확정 실패", "ABORT-" + token);
            paymentMapper.abortPayment(ready.paymentId(), PaymentStatus.ABORTED.name(),
                approval.providerStatus(), PaymentErrorCode.CONFIRM_FAILED.code(),
                cause.getMessage());
        } catch (RuntimeException cancelFailure) {
            log.error("주문제작 결제 보상 취소 실패 — 수동 확인 필요. paymentKey={}, token={}",
                approval.paymentKey(), token, cancelFailure);
        }
    }
}
