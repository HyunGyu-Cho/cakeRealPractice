package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
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
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
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
            couponService);
    }

    /** 준비 단계가 만들어 둔 READY 결제. 주문번호는 이 시점에 이미 정해져 있다. */
    private Payment readyPayment(CheckoutDraft draft, long amount) {
        Payment ready = new Payment();
        ready.setId(500L);
        ready.setTossOrderId("ORD-20260725-ABC");
        ready.setIdempotencyKey(draft.getCheckoutId());
        ready.setAmount(amount);
        ready.setStatus("READY");
        return ready;
    }

    private PaymentApproval approval(long amount) {
        return new PaymentApproval("MOCK-KEY", "ORD-20260725-ABC", amount, "MOCK_DONE",
            LocalDateTime.of(2026, 7, 25, 13, 20));
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
        when(orderService.createPaidOrder(draft, checkout, "ORD-20260725-ABC")).thenReturn(order);
        when(paymentMapper.confirmPayment(any(Payment.class))).thenReturn(1);

        Long orderId = processor.confirm(
            1L, draft, readyPayment(draft, 82_000L), approval(82_000L), "card");

        assertThat(orderId).isEqualTo(99L);
        verify(productService).decreaseStock(7L, 2);
        verify(cartService).removeCheckoutItems(1L, List.of(11L));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).confirmPayment(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(500L);
        assertThat(captor.getValue().getOrderId()).isEqualTo(99L);
        assertThat(captor.getValue().getAmount()).isEqualTo(82_000L);
        assertThat(captor.getValue().getMethod()).isEqualTo("CARD");
        assertThat(captor.getValue().getPaymentKey()).isEqualTo("MOCK-KEY");
        assertThat(captor.getValue().getProviderStatus()).isEqualTo("MOCK_DONE");
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
        when(orderService.createPaidOrder(draft, checkout, "ORD-20260725-ABC")).thenReturn(order);
        when(paymentMapper.confirmPayment(any(Payment.class))).thenReturn(1);

        processor.confirm(1L, draft, readyPayment(draft, 77_000L), approval(77_000L), "card");

        verify(couponService).use(1L, 5L, 99L, 82_000L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).confirmPayment(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(77_000L);
    }

    /** 준비와 승인 사이에 가격·쿠폰이 바뀐 경우. 승인 금액과 다른 주문을 만들지 않는다. */
    @Test
    void rejectsConfirmationWhenServerAmountNoLongerMatchesApprovedAmount() {
        CheckoutDraft draft = new CheckoutDraft(1L, List.of(11L));
        draft.setPickupAt(LocalDateTime.of(2026, 7, 27, 14, 0));
        CheckoutItemView item = new CheckoutItemView(
            11L, 7L, "딸기 케이크", "GENERAL", null, 2, 45_000L, 90_000L, 2, 3);
        CheckoutView checkout =
            new CheckoutView(draft.getCheckoutId(), List.of(item), 90_000L, 2, draft.getPickupAt());
        when(orderService.getCheckoutView(1L, draft)).thenReturn(checkout);

        assertThatThrownBy(() -> processor.confirm(
            1L, draft, readyPayment(draft, 82_000L), approval(82_000L), "card"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.AMOUNT_MISMATCH);
        verify(productService, never()).decreaseStock(any(), anyInt());
        verify(paymentMapper, never()).confirmPayment(any());
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
        when(orderService.createPaidOrder(draft, checkout, "ORD-20260725-ABC")).thenReturn(order);
        when(paymentMapper.confirmPayment(any(Payment.class))).thenReturn(1);

        processor.confirm(1L, draft, readyPayment(draft, 82_000L), approval(82_000L), "card");

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
