package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.view.OrderReferenceView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.payment.dto.view.PaymentListView;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
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

    public PaymentService(PaymentMapper paymentMapper, OrderService orderService,
                          MemberService memberService) {
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findOwnedOrderIdByIdempotency(
        Long memberId, String idempotencyKey) {
        return paymentMapper.findByIdempotencyKey(idempotencyKey).map(payment -> {
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
        Set<Long> orderIds = payments.stream().map(Payment::getOrderId).collect(Collectors.toSet());
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
