package com.cakeshop.domain.payment.service;

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
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {
    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final ProductService productService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public RefundService(OrderService orderService, PaymentMapper paymentMapper,
                         ProductService productService, NotificationService notificationService) {
        this(orderService, paymentMapper, productService, notificationService,
            Clock.systemDefaultZone());
    }

    public RefundService(OrderService orderService, PaymentMapper paymentMapper,
                         ProductService productService, NotificationService notificationService,
                         Clock clock) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.productService = productService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public void cancelByCustomer(Long memberId, Long orderId, String reason) {
        cancel(memberId, orderId, reason, false);
    }

    @Transactional
    public void cancelByAdmin(Long orderId, String reason) {
        cancel(null, orderId, reason, true);
    }

    private void cancel(Long memberId, Long orderId, String reason, boolean admin) {
        Order order = orderService.lockOrder(orderId);
        if (!admin && !order.getMemberId().equals(memberId)) {
            throw new BusinessException(OrderErrorCode.NOT_FOUND);
        }
        if (OrderStatus.CANCELED.name().equals(order.getStatus())) {
            return;
        }

        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        if (status != OrderStatus.PAID && status != OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException(OrderErrorCode.CANCELLATION_NOT_ALLOWED);
        }

        List<OrderItem> items = orderService.getOrderItems(orderId);
        if (!admin) {
            int limitDays = items.stream()
                .map(OrderItem::getCancellationLimitDays)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue).max().orElse(0);
            LocalDateTime deadline = order.getPickupAt().minusDays(limitDays);
            if (!LocalDateTime.now(clock).isBefore(deadline)) {
                throw new BusinessException(OrderErrorCode.CANCELLATION_DEADLINE_PASSED);
            }
        }

        Payment payment = paymentMapper.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));
        if (!PaymentStatus.DONE.name().equals(payment.getStatus())) {
            throw new BusinessException(OrderErrorCode.CANCELLATION_NOT_ALLOWED);
        }

        items.forEach(item -> productService.restoreStock(item.getProductId(), item.getQuantity()));
        if (paymentMapper.cancelPayment(payment.getId(), PaymentStatus.DONE.name()) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.setPaymentId(payment.getId());
        cancellation.setIdempotencyKey("CANCEL-ORDER-" + orderId);
        cancellation.setCancelAmount(payment.getAmount());
        cancellation.setCancelReason(reason.trim());
        cancellation.setStatus(PaymentCancellationStatus.DONE.name());
        cancellation.setTransactionKey("MOCK-CANCEL-" + UUID.randomUUID());
        cancellation.setCanceledAt(now);
        if (paymentMapper.insertCancellation(cancellation) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }
        orderService.markCanceled(order, reason.trim(), admin ? "ADMIN" : "MEMBER:" + memberId);
        notifyCanceled(order, admin);
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
}
