package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.entity.CustomOrderPaymentLink;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.PaymentLinkStatus;
import com.cakeshop.domain.order.entity.QuoteStatus;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.mapper.CustomOrderMapper;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.CustomOrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문제작 결제 링크 결제. 일반 결제와 같은 모의 결제이며 {@code payments} 행도 동일하게 만든다.
 *
 * <p>중복 결제를 3중으로 막는다.
 * <ol>
 *   <li>{@code uk_custom_order_payment_links_quote} — 견적당 링크 1건</li>
 *   <li>링크 행 {@code FOR UPDATE} 잠금 후 {@code ISSUED} 재확인</li>
 *   <li>{@code payments.idempotency_key}에 토큰을 넣어 UNIQUE 충돌 시 기존 주문 반환</li>
 * </ol>
 * 주문제작 상품은 {@code stock_quantity}가 NULL(재고 미관리)이라 재고 차감이 없다.
 */
@Service
public class CustomOrderPaymentService {

    private static final Set<String> METHODS = Set.of("CARD", "KAKAO", "NAVER", "TOSS", "BANK");

    private final OrderMapper orderMapper;
    private final CustomOrderMapper customOrderMapper;
    private final CustomOrderService customOrderService;
    private final PaymentMapper paymentMapper;
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public CustomOrderPaymentService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                                     CustomOrderService customOrderService, PaymentMapper paymentMapper,
                                     NotificationService notificationService) {
        this(orderMapper, customOrderMapper, customOrderService, paymentMapper,
            notificationService, Clock.systemDefaultZone());
    }

    public CustomOrderPaymentService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                                     CustomOrderService customOrderService, PaymentMapper paymentMapper,
                                     NotificationService notificationService, Clock clock) {
        this.orderMapper = orderMapper;
        this.customOrderMapper = customOrderMapper;
        this.customOrderService = customOrderService;
        this.paymentMapper = paymentMapper;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * 결제 화면 진입. 토큰만으로 통과시키지 않고 <b>로그인 회원이 주문 소유자인지 반드시 확인한다.</b>
     */
    @Transactional(readOnly = true)
    public CustomOrderDetailView getPayableRequest(String token, Long memberId) {
        CustomOrderPaymentLink link = customOrderMapper.findLinkByToken(token)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.LINK_NOT_FOUND));
        Order order = requireOwnedOrder(link, memberId);
        requirePayable(link, order);
        return customOrderService.getMyRequest(memberId, order.getId());
    }

    /**
     * 결제 완료 트랜잭션 — 링크 {@code USED}, 견적 금액 확정, 주문 {@code UNDER_REVIEW → IN_PRODUCTION},
     * 결제 {@code DONE}을 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public Long pay(String token, Long memberId, String requestedMethod) {
        String method = normalizeMethod(requestedMethod);
        CustomOrderPaymentLink link = customOrderMapper.findLinkByTokenForUpdate(token)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.LINK_NOT_FOUND));
        Order order = requireOwnedOrder(link, memberId);
        requirePayable(link, order);

        LocalDateTime now = LocalDateTime.now(clock);
        if (customOrderMapper.updateLinkStatus(link.getId(), PaymentLinkStatus.ISSUED.name(),
            PaymentLinkStatus.USED.name(), now) != 1) {
            throw new BusinessException(CustomOrderErrorCode.LINK_NOT_PAYABLE);
        }

        customOrderMapper.updateFinalAmount(order.getId(), link.getAmount());
        if (orderMapper.updateStatus(order.getId(), OrderStatus.UNDER_REVIEW.name(),
            OrderStatus.IN_PRODUCTION.name()) != 1) {
            throw new BusinessException(CustomOrderErrorCode.NOT_UNDER_REVIEW);
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setTossOrderId(order.getOrderNumber());
        payment.setPaymentKey("MOCK-" + UUID.randomUUID());
        // 토큰을 멱등 키로 써서 같은 링크의 중복 결제가 DB에서 걸리게 한다.
        payment.setIdempotencyKey(token);
        payment.setMethod(method);
        payment.setAmount(link.getAmount());
        payment.setStatus(PaymentStatus.DONE.name());
        payment.setProviderStatus("MOCK_DONE");
        payment.setApprovedAt(now);
        try {
            if (paymentMapper.insertPayment(payment) != 1) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            // 같은 토큰으로 이미 결제됐다. 롤백하고 기존 결제의 주문을 알린다.
            throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
        }

        notifyPaid(order);
        return order.getId();
    }

    private void notifyPaid(Order order) {
        notificationService.notify(NotificationCommand.forOrder(
            order.getMemberId(), NotificationType.ORDER_PAID, order.getId(),
            NotificationType.ORDER_PAID.label(),
            "주문제작 " + order.getOrderNumber() + " 결제가 완료되어 제작을 시작합니다."));
        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_ORDER_PLACED,
            NotificationType.ADMIN_ORDER_PLACED.label(),
            "주문제작 " + order.getOrderNumber() + " 결제가 완료되었습니다.",
            "/admin/custom-orders/" + order.getId(), order.getId(), null));
    }

    private Order requireOwnedOrder(CustomOrderPaymentLink link, Long memberId) {
        CustomOrderQuote quote = customOrderMapper.findQuoteById(link.getQuoteId())
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.QUOTE_NOT_FOUND));
        return orderMapper.findByIdAndMemberId(quote.getOrderId(), memberId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
    }

    private void requirePayable(CustomOrderPaymentLink link, Order order) {
        if (OrderStatus.IN_PRODUCTION.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
        }
        if (!OrderStatus.UNDER_REVIEW.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.NOT_UNDER_REVIEW);
        }
        if (!PaymentLinkStatus.ISSUED.name().equals(link.getStatus())) {
            throw new BusinessException(PaymentLinkStatus.USED.name().equals(link.getStatus())
                ? CustomOrderErrorCode.ALREADY_PAID
                : CustomOrderErrorCode.LINK_NOT_PAYABLE);
        }
        if (LocalDateTime.now(clock).isAfter(link.getExpiresAt())) {
            throw new BusinessException(CustomOrderErrorCode.LINK_EXPIRED);
        }
    }

    private String normalizeMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(normalized)) {
            throw new BusinessException(PaymentErrorCode.INVALID_METHOD);
        }
        return normalized;
    }
}
