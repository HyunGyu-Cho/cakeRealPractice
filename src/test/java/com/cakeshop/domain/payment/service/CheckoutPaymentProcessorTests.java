package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutItemView;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutPaymentProcessorTests {
    @Mock OrderService orderService;
    @Mock CartService cartService;
    @Mock ProductService productService;
    @Mock PaymentMapper paymentMapper;
    @Mock NotificationService notificationService;
    @Mock CouponService couponService;

    private CheckoutPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CheckoutPaymentProcessor(
            orderService, cartService, productService, paymentMapper, notificationService,
            couponService,
            Clock.fixed(Instant.parse("2026-07-25T04:20:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void createsPaidOrderAndDonePaymentUsingServerCalculatedLatestPrice() {
        CheckoutDraft draft = new CheckoutDraft(1L, List.of(11L));
        draft.setPickupAt(LocalDateTime.of(2026, 7, 27, 14, 0));
        CheckoutItemView item = new CheckoutItemView(
            11L, 7L, "딸기 케이크", "GENERAL", null, 2, 41_000L, 82_000L, 2, 3);
        CheckoutView checkout =
            new CheckoutView(draft.getCheckoutId(), List.of(item), 82_000L, 2, draft.getPickupAt());
        when(orderService.getCheckoutView(1L, draft)).thenReturn(checkout);
        Order order = new Order();
        order.setId(99L);
        order.setOrderNumber("ORD-20260725-ABC");
        when(orderService.createPaidOrder(draft, checkout)).thenReturn(order);
        when(paymentMapper.insertPayment(any(Payment.class))).thenReturn(1);

        Long orderId = processor.process(1L, draft, "card");

        assertThat(orderId).isEqualTo(99L);
        verify(productService).decreaseStock(7L, 2);
        verify(cartService).removeCheckoutItems(1L, List.of(11L));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(82_000L);
        assertThat(captor.getValue().getStatus()).isEqualTo("DONE");
        assertThat(captor.getValue().getMethod()).isEqualTo("CARD");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(draft.getCheckoutId());
        assertThat(captor.getValue().getPaymentKey()).startsWith("MOCK-");
    }

    @Test
    void appliesCouponDiscountToPaymentAmountAndConfirmsUseInTheSameTransaction() {
        CheckoutDraft draft = new CheckoutDraft(1L, List.of(11L));
        draft.setPickupAt(LocalDateTime.of(2026, 7, 27, 14, 0));
        draft.setMemberCouponId(5L);
        CheckoutItemView item = new CheckoutItemView(
            11L, 7L, "딸기 케이크", "GENERAL", null, 2, 41_000L, 82_000L, 2, 3);
        AvailableCouponView coupon = new AvailableCouponView(
            5L, "5천원 할인", "5,000원 할인", 5_000L, 20_000L,
            LocalDateTime.of(2026, 12, 31, 0, 0));
        CheckoutView checkout = new CheckoutView(draft.getCheckoutId(), List.of(item), 82_000L, 2,
            draft.getPickupAt(), List.of(coupon), 5L, 5_000L, 77_000L);
        when(orderService.getCheckoutView(1L, draft)).thenReturn(checkout);
        Order order = new Order();
        order.setId(99L);
        order.setOrderNumber("ORD-20260725-ABC");
        when(orderService.createPaidOrder(draft, checkout)).thenReturn(order);
        when(paymentMapper.insertPayment(any(Payment.class))).thenReturn(1);

        processor.process(1L, draft, "card");

        verify(couponService).use(1L, 5L, 99L, 82_000L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(77_000L);
    }

    @Test
    void paymentSuccessNotifiesCustomerAndAdmins() {
        CheckoutDraft draft = new CheckoutDraft(1L, List.of(11L));
        draft.setPickupAt(LocalDateTime.of(2026, 7, 27, 14, 0));
        CheckoutItemView item = new CheckoutItemView(
            11L, 7L, "딸기 케이크", "GENERAL", null, 2, 41_000L, 82_000L, 2, 3);
        CheckoutView checkout =
            new CheckoutView(draft.getCheckoutId(), List.of(item), 82_000L, 2, draft.getPickupAt());
        when(orderService.getCheckoutView(1L, draft)).thenReturn(checkout);
        Order order = new Order();
        order.setId(99L);
        order.setMemberId(1L);
        order.setOrderNumber("ORD-20260725-ABC");
        when(orderService.createPaidOrder(draft, checkout)).thenReturn(order);
        when(paymentMapper.insertPayment(any(Payment.class))).thenReturn(1);

        processor.process(1L, draft, "card");

        ArgumentCaptor<NotificationCommand> customer =
            ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(customer.capture());
        assertThat(customer.getValue().receiverId()).isEqualTo(1L);
        assertThat(customer.getValue().type()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(customer.getValue().orderId()).isEqualTo(99L);

        ArgumentCaptor<NotificationCommand> admins =
            ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notifyAdmins(admins.capture());
        assertThat(admins.getValue().receiverId()).isNull();
        assertThat(admins.getValue().type()).isEqualTo(NotificationType.ADMIN_ORDER_PLACED);
    }
}
