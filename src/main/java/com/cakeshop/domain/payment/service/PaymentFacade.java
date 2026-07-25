package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.global.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PaymentFacade {
    private final PaymentService paymentService;
    private final CheckoutPaymentProcessor processor;

    public PaymentFacade(PaymentService paymentService, CheckoutPaymentProcessor processor) {
        this.paymentService = paymentService;
        this.processor = processor;
    }

    public Long pay(Long memberId, String checkoutId, CheckoutDraft draft, String method) {
        Long completed = findCompleted(memberId, checkoutId);
        if (completed != null) {
            return completed;
        }
        if (draft == null || !draft.getCheckoutId().equals(checkoutId)) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }

        try {
            return processor.process(memberId, draft, method);
        } catch (DataIntegrityViolationException duplicate) {
            Long concurrentResult = findCompleted(memberId, checkoutId);
            if (concurrentResult != null) {
                return concurrentResult;
            }
            throw duplicate;
        }
    }

    private Long findCompleted(Long memberId, String checkoutId) {
        if (checkoutId == null || checkoutId.isBlank()) {
            return null;
        }
        return paymentService.findOwnedOrderIdByIdempotency(memberId, checkoutId).orElse(null);
    }
}
