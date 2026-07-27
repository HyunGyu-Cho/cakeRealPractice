package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentCancellationStatus;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentCancel;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소의 DB 쪽 절반. 외부 취소 호출은 {@link RefundService}가 이 둘 사이에서 한다.
 *
 * <p>검증과 확정이 다른 트랜잭션이라 잠금이 이어지지 않는다. 그래서 확정 단계가 잠금을 다시 잡고
 * 조건을 처음부터 다시 본다. 그 사이 중복 요청은 {@code uk_payment_cancellations_idempotency}
 * (`CANCEL-ORDER-{orderId}`)가 막는다.
 */
@Service
public class RefundProcessor {
    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final ProductService productService;
    private final NotificationService notificationService;
    private final CouponService couponService;
    private final Clock clock;

    @Autowired
    public RefundProcessor(OrderService orderService, PaymentMapper paymentMapper,
                           ProductService productService, NotificationService notificationService,
                           CouponService couponService) {
        this(orderService, paymentMapper, productService, notificationService, couponService,
            Clock.systemDefaultZone());
    }

    public RefundProcessor(OrderService orderService, PaymentMapper paymentMapper,
                           ProductService productService, NotificationService notificationService,
                           CouponService couponService, Clock clock) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.productService = productService;
        this.notificationService = notificationService;
        this.couponService = couponService;
        this.clock = clock;
    }

    /** 취소 가능 여부를 확인하고 외부 취소에 필요한 값을 돌려준다. 이미 취소된 주문이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<CancelTarget> validate(Long memberId, Long orderId, boolean admin) {
        return validateOrder(orderService.lockOrder(orderId), memberId, admin)
            .map(payment -> new CancelTarget(payment.getId(), payment.getPaymentKey(),
                payment.getAmount()));
    }

    @Transactional
    public void confirm(Long memberId, Long orderId, String reason, boolean admin,
                        PaymentCancel canceled) {
        Order order = orderService.lockOrder(orderId);
        Optional<Payment> target = validateOrder(order, memberId, admin);
        if (target.isEmpty()) {
            return;
        }
        Payment payment = target.get();

        List<OrderItem> items = orderService.getOrderItems(orderId);
        items.forEach(item -> productService.restoreStock(item.getProductId(), item.getQuantity()));
        if (paymentMapper.cancelPayment(payment.getId(), PaymentStatus.DONE.name(),
            canceled.providerStatus()) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }

        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.setPaymentId(payment.getId());
        // 결정적 키 — 반복 요청이 재고를 두 번 복구하지 못하게 DB가 막는다.
        cancellation.setIdempotencyKey(cancelIdempotencyKey(orderId));
        cancellation.setCancelAmount(payment.getAmount());
        cancellation.setCancelReason(reason.trim());
        cancellation.setStatus(PaymentCancellationStatus.DONE.name());
        cancellation.setTransactionKey(canceled.transactionKey());
        cancellation.setCanceledAt(
            canceled.canceledAt() == null ? LocalDateTime.now(clock) : canceled.canceledAt());
        if (paymentMapper.insertCancellation(cancellation) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }
        // 쓴 쿠폰이 있으면 같은 트랜잭션에서 되돌린다. 기간이 남아 있으면 다시 쓸 수 있다.
        couponService.restoreByOrderId(orderId);
        orderService.markCanceled(order, reason.trim(), admin ? "ADMIN" : "MEMBER:" + memberId);
        notifyCanceled(order, admin);
    }

    public static String cancelIdempotencyKey(Long orderId) {
        return "CANCEL-ORDER-" + orderId;
    }

    /** 취소 가능한 결제를 돌려준다. 이미 취소된 주문은 비어 있고(멱등), 규칙 위반은 예외다. */
    private Optional<Payment> validateOrder(Order order, Long memberId, boolean admin) {
        if (!admin && !order.getMemberId().equals(memberId)) {
            throw new BusinessException(OrderErrorCode.NOT_FOUND);
        }
        if (OrderStatus.CANCELED.name().equals(order.getStatus())) {
            return Optional.empty();
        }

        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        if (status != OrderStatus.PAID && status != OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException(OrderErrorCode.CANCELLATION_NOT_ALLOWED);
        }

        if (!admin) {
            List<OrderItem> items = orderService.getOrderItems(order.getId());
            int limitDays = items.stream()
                .map(OrderItem::getCancellationLimitDays)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue).max().orElse(0);
            LocalDateTime deadline = order.getPickupAt().minusDays(limitDays);
            if (!LocalDateTime.now(clock).isBefore(deadline)) {
                throw new BusinessException(OrderErrorCode.CANCELLATION_DEADLINE_PASSED);
            }
        }

        Payment payment = paymentMapper.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));
        if (!PaymentStatus.DONE.name().equals(payment.getStatus())) {
            throw new BusinessException(OrderErrorCode.CANCELLATION_NOT_ALLOWED);
        }
        return Optional.of(payment);
    }

    /** 취소·환불 완료를 고객에게 알리고, 고객이 직접 취소한 경우 관리자에게도 알린다. */
    private void notifyCanceled(Order order, boolean admin) {
        notificationService.notify(NotificationCommand.forOrder(
            order.getMemberId(), NotificationType.ORDER_CANCELED, order.getId(),
            NotificationType.ORDER_CANCELED.label(),
            "주문 " + order.getOrderNumber() + " 취소 및 환불이 처리되었습니다."));
        if (!admin) {
            notificationService.notifyAdmins(NotificationCommand.toAdmins(
                NotificationType.ADMIN_ORDER_CANCELED,
                NotificationType.ADMIN_ORDER_CANCELED.label(),
                "고객이 주문 " + order.getOrderNumber() + "을 취소했습니다.",
                "/admin/orders/" + order.getId(), order.getId(), null));
        }
    }

    /** 외부 취소 호출에 필요한 값. */
    public record CancelTarget(Long paymentId, String paymentKey, Long amount) {
    }
}
