package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 확정의 경계. 외부 승인·취소 호출은 전부 여기서, DB 트랜잭션 밖에서 일어난다.
 *
 * <p>승인은 성사됐는데 우리 확정 트랜잭션이 실패하면 돈만 빠져나간 상태가 된다.
 * 그래서 실패를 잡아 즉시 보상 취소를 호출하고 결제를 {@code ABORTED}로 마감한다.
 */
@Service
public class PaymentFacade {
    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final PaymentService paymentService;
    private final CheckoutPaymentProcessor processor;
    private final PaymentMapper paymentMapper;
    private final PaymentGateway gateway;

    public PaymentFacade(PaymentService paymentService, CheckoutPaymentProcessor processor,
                         PaymentMapper paymentMapper, PaymentGateway gateway) {
        this.paymentService = paymentService;
        this.processor = processor;
        this.paymentMapper = paymentMapper;
        this.gateway = gateway;
    }

    /**
     * 모의 결제 경로 — 결제창이 없어 폼 제출이 곧 승인 요청이다.
     * 준비 단계가 만들어 둔 READY 행의 주문번호·금액을 그대로 쓴다.
     */
    public Long pay(Long memberId, String checkoutId, CheckoutDraft draft, String method) {
        Long completed = findCompleted(memberId, checkoutId);
        if (completed != null) {
            return completed;
        }
        Payment ready = requireReady(draft, checkoutId);
        return approveAndConfirm(memberId, draft, ready, null, method);
    }

    /**
     * 실결제 콜백 경로 — 제공자가 승인 가능한 결제라며 돌려보낸 값을 검증하고 승인한다.
     *
     * @param tossOrderId 콜백이 돌려준 주문번호. 우리가 준비 단계에서 만든 값이어야 한다.
     * @param amount      콜백이 돌려준 금액. 저장된 금액과 다르면 승인하지 않는다.
     */
    public Long confirm(Long memberId, CheckoutDraft draft, String paymentKey,
                        String tossOrderId, long amount, String method) {
        Payment ready = paymentMapper.findByTossOrderId(tossOrderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));

        // 이미 확정된 결제로 다시 들어왔다(새로고침·뒤로가기). 승인을 다시 호출하지 않는다.
        if (PaymentStatus.DONE.name().equals(ready.getStatus()) && ready.getOrderId() != null) {
            return paymentService.findOwnedOrderIdByIdempotency(memberId, ready.getIdempotencyKey())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));
        }
        if (!PaymentStatus.READY.name().equals(ready.getStatus())) {
            throw new BusinessException(PaymentErrorCode.NOT_READY);
        }
        if (draft == null || !ready.getIdempotencyKey().equals(draft.getCheckoutId())) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }
        // 화면이 금액을 바꿔 결제했더라도 여기서 걸린다. 승인 자체를 하지 않는다.
        if (ready.getAmount() == null || ready.getAmount() != amount) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        return approveAndConfirm(memberId, draft, ready, paymentKey, method);
    }

    /** 결제창 실패·이탈. 준비된 결제를 마감해 같은 체크아웃이 다시 열리지 않게 한다. */
    public void markFailed(String tossOrderId, String code, String message) {
        paymentMapper.findByTossOrderId(tossOrderId).ifPresent(payment ->
            paymentMapper.abortPayment(payment.getId(), PaymentStatus.ABORTED.name(),
                code, code, message));
    }

    private Long approveAndConfirm(Long memberId, CheckoutDraft draft, Payment ready,
                                   String paymentKey, String method) {
        PaymentApproval approval = gateway.approve(paymentKey, ready.getTossOrderId(),
            ready.getAmount(), ready.getIdempotencyKey());
        try {
            return processor.confirm(memberId, draft, ready, approval, method);
        } catch (DataIntegrityViolationException duplicate) {
            // 동시 요청 중 다른 쪽이 먼저 확정했다면 그 주문을 돌려준다.
            Long concurrent = findCompleted(memberId, ready.getIdempotencyKey());
            if (concurrent != null) {
                return concurrent;
            }
            throw compensate(ready, approval, duplicate);
        } catch (RuntimeException e) {
            throw compensate(ready, approval, e);
        }
    }

    /**
     * 승인은 됐는데 확정이 실패한 경우의 보상. 취소까지 실패하면 사람이 개입해야 하므로 로그를 남긴다
     * (결제 행은 READY로 남아 상태 대조 배치가 다시 집는다).
     */
    private BusinessException compensate(Payment ready, PaymentApproval approval, RuntimeException cause) {
        try {
            gateway.cancel(approval.paymentKey(), "주문 확정 실패", "ABORT-" + ready.getIdempotencyKey());
            paymentMapper.abortPayment(ready.getId(), PaymentStatus.ABORTED.name(),
                approval.providerStatus(), PaymentErrorCode.CONFIRM_FAILED.code(),
                cause.getMessage());
        } catch (RuntimeException cancelFailure) {
            log.error("결제 보상 취소 실패 — 수동 확인 필요. paymentKey={}, tossOrderId={}",
                approval.paymentKey(), ready.getTossOrderId(), cancelFailure);
        }
        return new BusinessException(PaymentErrorCode.CONFIRM_FAILED, cause);
    }

    private Payment requireReady(CheckoutDraft draft, String checkoutId) {
        if (draft == null || !draft.getCheckoutId().equals(checkoutId)) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }
        Payment ready = paymentMapper.findByIdempotencyKey(checkoutId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_READY));
        if (!PaymentStatus.READY.name().equals(ready.getStatus())) {
            throw new BusinessException(PaymentErrorCode.NOT_READY);
        }
        return ready;
    }

    private Long findCompleted(Long memberId, String checkoutId) {
        if (checkoutId == null || checkoutId.isBlank()) {
            return null;
        }
        return paymentService.findOwnedOrderIdByIdempotency(memberId, checkoutId).orElse(null);
    }
}
