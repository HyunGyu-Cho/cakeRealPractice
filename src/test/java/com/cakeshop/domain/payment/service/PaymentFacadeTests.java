package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.infra.PaymentCancel;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 실결제 콜백 경로의 검증·멱등·보상. 외부 호출은 {@link PaymentGateway} 목으로 대신한다 —
 * 토스 테스트 키 없이도 승인 실패·확정 실패의 결과를 확인할 수 있어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentFacadeTests {
    private static final String TOSS_ORDER_ID = "ORD-20260727-ABC";

    @Mock PaymentService paymentService;
    @Mock CheckoutPaymentProcessor processor;
    @Mock PaymentMapper paymentMapper;
    @Mock PaymentGateway gateway;

    private PaymentFacade facade;
    private CheckoutDraft draft;

    @BeforeEach
    void setUp() {
        facade = new PaymentFacade(paymentService, processor, paymentMapper, gateway);
        draft = new CheckoutDraft(1L, List.of(11L));
        when(gateway.approve(anyString(), anyString(), anyLong(), anyString()))
            .thenAnswer(invocation -> new PaymentApproval(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2), "DONE",
                LocalDateTime.of(2026, 7, 27, 14, 0)));
        when(gateway.cancel(anyString(), anyString(), anyString()))
            .thenReturn(new PaymentCancel("TX-1", "CANCELED", LocalDateTime.of(2026, 7, 27, 14, 1)));
    }

    private Payment ready(long amount) {
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setTossOrderId(TOSS_ORDER_ID);
        payment.setIdempotencyKey(draft.getCheckoutId());
        payment.setAmount(amount);
        payment.setStatus("READY");
        return payment;
    }

    @Test
    void confirmApprovesAndDelegatesToTheConfirmationTransaction() {
        when(paymentMapper.findByTossOrderId(TOSS_ORDER_ID)).thenReturn(Optional.of(ready(82_000L)));
        when(processor.confirm(eq(1L), eq(draft), any(), any(), eq("CARD"))).thenReturn(99L);

        Long orderId = facade.confirm(1L, draft, "PK-1", TOSS_ORDER_ID, 82_000L, "CARD");

        assertThat(orderId).isEqualTo(99L);
        verify(gateway).approve("PK-1", TOSS_ORDER_ID, 82_000L, draft.getCheckoutId());
    }

    /** 화면이 금액을 바꿔 보낸 경우. 승인 자체를 하지 않는다. */
    @Test
    void confirmRejectsAmountMismatchWithoutCallingApprove() {
        when(paymentMapper.findByTossOrderId(TOSS_ORDER_ID)).thenReturn(Optional.of(ready(82_000L)));

        assertThatThrownBy(() -> facade.confirm(1L, draft, "PK-1", TOSS_ORDER_ID, 1_000L, "CARD"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.AMOUNT_MISMATCH);
        verify(gateway, never()).approve(any(), any(), anyLong(), any());
    }

    /** 새로고침·뒤로가기로 성공 콜백이 다시 들어온 경우. 승인을 다시 부르지 않는다. */
    @Test
    void confirmIsIdempotentForAlreadyConfirmedPayment() {
        Payment done = ready(82_000L);
        done.setStatus("DONE");
        done.setOrderId(99L);
        when(paymentMapper.findByTossOrderId(TOSS_ORDER_ID)).thenReturn(Optional.of(done));
        when(paymentService.findOwnedOrderIdByIdempotency(1L, draft.getCheckoutId()))
            .thenReturn(Optional.of(99L));

        Long orderId = facade.confirm(1L, draft, "PK-1", TOSS_ORDER_ID, 82_000L, "CARD");

        assertThat(orderId).isEqualTo(99L);
        verify(gateway, never()).approve(any(), any(), anyLong(), any());
        verify(processor, never()).confirm(any(), any(), any(), any(), any());
    }

    /** 승인은 됐는데 확정이 실패했다. 돈이 남지 않도록 즉시 보상 취소하고 ABORTED로 마감한다. */
    @Test
    void failedConfirmationCompensatesWithCancelAndAbortsPayment() {
        when(paymentMapper.findByTossOrderId(TOSS_ORDER_ID)).thenReturn(Optional.of(ready(82_000L)));
        when(processor.confirm(eq(1L), eq(draft), any(), any(), eq("CARD")))
            .thenThrow(new IllegalStateException("재고 부족"));

        assertThatThrownBy(() -> facade.confirm(1L, draft, "PK-1", TOSS_ORDER_ID, 82_000L, "CARD"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.CONFIRM_FAILED);

        verify(gateway).cancel("PK-1", "주문 확정 실패", "ABORT-" + draft.getCheckoutId());
        verify(paymentMapper).abortPayment(eq(500L), eq("ABORTED"), eq("DONE"),
            eq(PaymentErrorCode.CONFIRM_FAILED.code()), anyString());
    }

    @Test
    void failCallbackAbortsThePreparedPayment() {
        when(paymentMapper.findByTossOrderId(TOSS_ORDER_ID)).thenReturn(Optional.of(ready(82_000L)));

        facade.markFailed(TOSS_ORDER_ID, "PAY_PROCESS_CANCELED", "사용자가 결제를 취소했습니다.");

        verify(paymentMapper).abortPayment(500L, "ABORTED", "PAY_PROCESS_CANCELED",
            "PAY_PROCESS_CANCELED", "사용자가 결제를 취소했습니다.");
    }
}
