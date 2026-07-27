package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.dto.view.OrderReferenceView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.payment.dto.view.PaymentListView;
import com.cakeshop.domain.payment.dto.view.PaymentPrepareView;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final MemberService memberService;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentMapper paymentMapper, OrderService orderService,
                          MemberService memberService, PaymentGateway paymentGateway) {
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
        this.memberService = memberService;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 결제 준비 — {@code READY} 결제 행을 만들고 화면이 결제를 시작할 값을 돌려준다.
     *
     * <p>같은 체크아웃으로 다시 들어오면(뒤로가기·새로고침) 기존 READY 행을 재사용하고 금액만 맞춘다.
     * 매번 새 행을 만들면 {@code uk_payments_idempotency}에 걸린다.
     */
    @Transactional
    public PaymentPrepareView prepare(Long memberId, CheckoutDraft draft) {
        orderService.validateReadyForPayment(memberId, draft);
        CheckoutView checkout = orderService.getCheckoutView(memberId, draft);
        long amount = checkout.finalAmount();

        Payment existing = paymentMapper.findByIdempotencyKey(draft.getCheckoutId()).orElse(null);
        if (existing != null) {
            if (!PaymentStatus.READY.name().equals(existing.getStatus())) {
                // 이미 승인됐거나 마감된 체크아웃이다. 결제를 다시 열어 주지 않는다.
                throw new BusinessException(PaymentErrorCode.NOT_READY);
            }
            if (existing.getAmount() == null || existing.getAmount() != amount) {
                paymentMapper.updateReadyAmount(existing.getId(), amount);
            }
            return toPrepareView(existing.getTossOrderId(), amount, checkout);
        }

        Payment payment = new Payment();
        payment.setTossOrderId(orderService.newOrderNumber());
        payment.setIdempotencyKey(draft.getCheckoutId());
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.READY.name());
        if (paymentMapper.insertPayment(payment) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }
        return toPrepareView(payment.getTossOrderId(), amount, checkout);
    }

    private PaymentPrepareView toPrepareView(String tossOrderId, long amount, CheckoutView checkout) {
        return new PaymentPrepareView(paymentGateway.provider(), paymentGateway.clientKey(),
            tossOrderId, amount, orderName(checkout));
    }

    /** 제공자 화면에 표시되는 주문명. 첫 상품 기준으로 짧게 만든다. */
    private String orderName(CheckoutView checkout) {
        if (checkout.items().isEmpty()) {
            return "주문";
        }
        String first = checkout.items().get(0).productName();
        int rest = checkout.items().size() - 1;
        return rest == 0 ? first : first + " 외 " + rest + "건";
    }

    @Transactional(readOnly = true)
    public Optional<Long> findOwnedOrderIdByIdempotency(
        Long memberId, String idempotencyKey) {
        return paymentMapper.findByIdempotencyKey(idempotencyKey)
            // 준비만 된 READY 행에는 아직 주문이 없다. "완료된 결제"로 취급하면 안 된다.
            .filter(payment -> payment.getOrderId() != null)
            .map(payment -> {
                OrderReferenceView order = orderService.getOrderReference(payment.getOrderId());
                if (!order.memberId().equals(memberId)) {
                    throw new BusinessException(PaymentErrorCode.NOT_FOUND);
                }
                return order.id();
            });
    }

    @Transactional(readOnly = true)
    public Payment getPaymentForOrder(Long orderId) {
        return paymentMapper.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResult<PaymentListView> getPaymentPage(
        PaymentSearchForm cond, PageRequest pageRequest) {
        long total = paymentMapper.countPayments(cond);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<Payment> payments =
            paymentMapper.findPaymentPage(cond, pageRequest.getSize(), pageRequest.getOffset());
        // 준비만 되고 승인되지 않은 READY 결제에는 주문이 없다. 조회 대상에서 뺀다.
        Set<Long> orderIds = payments.stream().map(Payment::getOrderId)
            .filter(orderId -> orderId != null).collect(Collectors.toSet());
        Map<Long, OrderReferenceView> orders = orderService.getOrderReferenceMap(orderIds);
        Set<Long> memberIds = orders.values().stream()
            .map(OrderReferenceView::memberId).collect(Collectors.toSet());
        Map<Long, String> memberNames = memberService.getNicknameMap(memberIds);
        Map<Long, PaymentCancellation> cancellations = paymentMapper
            .findCancellationsByPaymentIds(
                payments.stream().map(Payment::getId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(
                PaymentCancellation::getPaymentId,
                cancellation -> cancellation,
                (newer, older) -> newer));
        List<PaymentListView> content = payments.stream().map(payment -> {
            OrderReferenceView order = orders.get(payment.getOrderId());
            PaymentCancellation cancellation = cancellations.get(payment.getId());
            return new PaymentListView(
                payment.getId(), payment.getOrderId(), payment.getPaymentKey(),
                order == null ? "-" : order.orderNumber(),
                order == null ? null : order.memberId(),
                order == null ? "-" : memberNames.getOrDefault(order.memberId(), "탈퇴 회원"),
                payment.getMethod(), payment.getAmount(), payment.getStatus(),
                payment.getApprovedAt(), payment.getCanceledAt(),
                cancellation == null ? null : cancellation.getStatus(),
                cancellation == null ? null : cancellation.getCancelReason());
        }).toList();
        return new PageResult<>(content, pageRequest, total);
    }
}
