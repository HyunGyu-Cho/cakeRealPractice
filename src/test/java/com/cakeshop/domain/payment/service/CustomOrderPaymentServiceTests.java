package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.view.CouponDiscount;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.entity.CustomOrderPaymentLink;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.PaymentLinkStatus;
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
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOrderPaymentServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Long MEMBER_ID = 7L;
    private static final String TOKEN = "tok-abc";

    @Mock private OrderMapper orderMapper;
    @Mock private CustomOrderMapper customOrderMapper;
    @Mock private CustomOrderService customOrderService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private NotificationService notificationService;
    @Mock private CouponService couponService;

    private CustomOrderPaymentService paymentService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        paymentService = new CustomOrderPaymentService(orderMapper, customOrderMapper,
            customOrderService, paymentMapper, notificationService, couponService, clock);
        // 쿠폰 미선택이 기본 경로다 — 할인 없이 견적 금액 그대로 결제한다.
        when(couponService.use(eq(MEMBER_ID), isNull(), anyLong(), anyLong()))
            .thenAnswer(invocation -> CouponDiscount.none(invocation.getArgument(3)));

        CustomOrderQuote quote = new CustomOrderQuote();
        quote.setId(5L);
        quote.setOrderId(100L);
        when(customOrderMapper.findQuoteById(5L)).thenReturn(Optional.of(quote));
        when(paymentMapper.insertPayment(any())).thenReturn(1);
        when(customOrderMapper.updateLinkStatus(anyLong(), eq("ISSUED"), eq("USED"), any()))
            .thenReturn(1);
        when(orderMapper.updateStatus(100L, "UNDER_REVIEW", "IN_PRODUCTION")).thenReturn(1);
    }

    @Test
    void payMovesOrderToProductionAndUsesTokenAsIdempotencyKey() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);

        Long orderId = paymentService.pay(TOKEN, MEMBER_ID, "CARD", null);

        assertThat(orderId).isEqualTo(100L);
        verify(customOrderMapper).updateLinkStatus(1L, "ISSUED", "USED", NOW);
        verify(customOrderMapper).updateAmounts(100L, 180_000L, 0L, 180_000L);
        verify(orderMapper).updateStatus(100L, "UNDER_REVIEW", "IN_PRODUCTION");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        Payment payment = captor.getValue();
        // 토큰을 멱등 키로 써서 같은 링크의 중복 결제가 DB UNIQUE에서 걸린다
        assertThat(payment.getIdempotencyKey()).isEqualTo(TOKEN);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE.name());
        assertThat(payment.getAmount()).isEqualTo(180_000L);
    }

    @Test
    void payUsesLinkAmountNotOrderEstimate() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        Order order = givenOrder(OrderStatus.UNDER_REVIEW);
        order.setFinalAmount(62_000L); // 요청서 제출 시점의 예상 금액

        paymentService.pay(TOKEN, MEMBER_ID, "CARD", null);

        // 결제 금액의 정본은 발급 시점 견적 스냅샷(링크 금액)이지 주문의 예상 금액이 아니다
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(180_000L);
        verify(customOrderMapper).updateAmounts(100L, 180_000L, 0L, 180_000L);
    }

    @Test
    void payRejectsExpiredLink() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.minusMinutes(1));
        givenOrder(OrderStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.LINK_EXPIRED);
        verify(paymentMapper, never()).insertPayment(any());
    }

    @Test
    void payRejectsAlreadyUsedLink() {
        givenLink(PaymentLinkStatus.USED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.ALREADY_PAID);
    }

    @Test
    void payRejectsRevokedLink() {
        givenLink(PaymentLinkStatus.REVOKED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.LINK_NOT_PAYABLE);
    }

    @Test
    void payRejectsOtherMembersLink() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        // 토큰은 맞지만 소유자가 다르다 — 토큰만으로 통과시키지 않는다
        when(orderMapper.findByIdAndMemberId(100L, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.REQUEST_NOT_FOUND);
    }

    @Test
    void payRejectsWhenOrderAlreadyInProduction() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.IN_PRODUCTION);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.ALREADY_PAID);
    }

    @Test
    void concurrentLinkUpdateLosesRace() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);
        // 조건부 UPDATE가 0행 → 다른 요청이 먼저 썼다
        when(customOrderMapper.updateLinkStatus(anyLong(), anyString(), anyString(), any()))
            .thenReturn(0);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.LINK_NOT_PAYABLE);
        verify(paymentMapper, never()).insertPayment(any());
    }

    @Test
    void duplicatePaymentKeyRollsBackAsAlreadyPaid() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(paymentMapper.insertPayment(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.ALREADY_PAID);
    }

    @Test
    void payRejectsUnknownMethod() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "BITCOIN", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.INVALID_METHOD);
    }

    @Test
    void payNotifiesCustomerAndAdmins() {
        givenLink(PaymentLinkStatus.ISSUED, NOW.plusHours(10));
        givenOrder(OrderStatus.UNDER_REVIEW);

        paymentService.pay(TOKEN, MEMBER_ID, "CARD", null);

        verify(notificationService).notify(any());
        verify(notificationService).notifyAdmins(any());
    }

    @Test
    void unknownTokenIsNotFound() {
        when(customOrderMapper.findLinkByTokenForUpdate(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.pay(TOKEN, MEMBER_ID, "CARD", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.LINK_NOT_FOUND);
    }

    private void givenLink(PaymentLinkStatus status, LocalDateTime expiresAt) {
        CustomOrderPaymentLink link = new CustomOrderPaymentLink();
        link.setId(1L);
        link.setQuoteId(5L);
        link.setToken(TOKEN);
        link.setAmount(180_000L);
        link.setExpiresAt(expiresAt);
        link.setStatus(status.name());
        when(customOrderMapper.findLinkByTokenForUpdate(TOKEN)).thenReturn(Optional.of(link));
        when(customOrderMapper.findLinkByToken(TOKEN)).thenReturn(Optional.of(link));
    }

    private Order givenOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("CUS-20260801-ABC");
        order.setMemberId(MEMBER_ID);
        order.setStatus(status.name());
        when(orderMapper.findByIdAndMemberId(100L, MEMBER_ID)).thenReturn(Optional.of(order));
        return order;
    }
}


