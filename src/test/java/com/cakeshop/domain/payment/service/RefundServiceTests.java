package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceTests {
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-07-25T04:20:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock OrderService orderService;
    @Mock PaymentMapper paymentMapper;
    @Mock ProductService productService;
    @Mock NotificationService notificationService;
    @Mock CouponService couponService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(orderService, paymentMapper, productService,
            notificationService, couponService, CLOCK);
    }

    @Test
    void customerCancellationRestoresStockAndRecordsFullRefund() {
        Order order = order("PAID", LocalDateTime.of(2026, 7, 29, 13, 20));
        OrderItem item = item(7L, 2, 3);
        when(orderService.lockOrder(9L)).thenReturn(order);
        when(orderService.getOrderItems(9L)).thenReturn(List.of(item));
        when(paymentMapper.findByOrderIdForUpdate(9L)).thenReturn(Optional.of(payment()));
        when(paymentMapper.cancelPayment(20L, "DONE")).thenReturn(1);
        when(paymentMapper.insertCancellation(any(PaymentCancellation.class))).thenReturn(1);

        refundService.cancelByCustomer(1L, 9L, "일정 변경");

        verify(productService).restoreStock(7L, 2);
        // 쓴 쿠폰은 같은 취소 트랜잭션에서 되돌린다.
        verify(couponService).restoreByOrderId(9L);
        verify(orderService).markCanceled(order, "일정 변경", "MEMBER:1");
        ArgumentCaptor<PaymentCancellation> captor =
            ArgumentCaptor.forClass(PaymentCancellation.class);
        verify(paymentMapper).insertCancellation(captor.capture());
        assertThat(captor.getValue().getCancelAmount()).isEqualTo(82_000L);
        assertThat(captor.getValue().getStatus()).isEqualTo("DONE");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("CANCEL-ORDER-9");

        ArgumentCaptor<NotificationCommand> customer =
            ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(customer.capture());
        assertThat(customer.getValue().receiverId()).isEqualTo(1L);
        assertThat(customer.getValue().type()).isEqualTo(NotificationType.ORDER_CANCELED);
        // 고객이 직접 취소하면 관리자에게도 알린다.
        verify(notificationService).notifyAdmins(any(NotificationCommand.class));
    }

    @Test
    void adminCancellationNotifiesOnlyTheCustomer() {
        Order order = order("PAID", LocalDateTime.of(2026, 7, 29, 13, 20));
        when(orderService.lockOrder(9L)).thenReturn(order);
        when(orderService.getOrderItems(9L)).thenReturn(List.of(item(7L, 2, 3)));
        when(paymentMapper.findByOrderIdForUpdate(9L)).thenReturn(Optional.of(payment()));
        when(paymentMapper.cancelPayment(20L, "DONE")).thenReturn(1);
        when(paymentMapper.insertCancellation(any(PaymentCancellation.class))).thenReturn(1);

        refundService.cancelByAdmin(9L, "재료 소진");

        verify(notificationService).notify(any(NotificationCommand.class));
        verify(notificationService, never()).notifyAdmins(any(NotificationCommand.class));
    }

    @Test
    void repeatedCancellationDoesNotNotifyAgain() {
        when(orderService.lockOrder(9L)).thenReturn(order("CANCELED", LocalDateTime.now(CLOCK)));

        refundService.cancelByCustomer(1L, 9L, "반복 요청");

        verify(notificationService, never()).notify(any(NotificationCommand.class));
    }

    @Test
    void cancellationIsBlockedAtExactDeadline() {
        // pickup - 3일 == 현재 시각: 경계부터 취소 불가
        Order order = order("PAID", LocalDateTime.of(2026, 7, 28, 13, 20));
        when(orderService.lockOrder(9L)).thenReturn(order);
        when(orderService.getOrderItems(9L)).thenReturn(List.of(item(7L, 2, 3)));

        assertThatThrownBy(() -> refundService.cancelByCustomer(1L, 9L, "일정 변경"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(OrderErrorCode.CANCELLATION_DEADLINE_PASSED));
        verify(productService, never()).restoreStock(any(), anyInt());
    }

    @Test
    void repeatedCancellationDoesNotRestoreStockAgain() {
        when(orderService.lockOrder(9L)).thenReturn(order("CANCELED", LocalDateTime.now(CLOCK)));

        refundService.cancelByCustomer(1L, 9L, "반복 요청");

        verify(productService, never()).restoreStock(any(), anyInt());
        verify(paymentMapper, never()).findByOrderIdForUpdate(any());
    }

    private Order order(String status, LocalDateTime pickupAt) {
        Order order = new Order();
        order.setId(9L);
        order.setMemberId(1L);
        order.setStatus(status);
        order.setPickupAt(pickupAt);
        return order;
    }

    private OrderItem item(Long productId, int quantity, int cancellationDays) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setCancellationLimitDays(cancellationDays);
        return item;
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrderId(9L);
        payment.setAmount(82_000L);
        payment.setStatus("DONE");
        return payment;
    }
}
