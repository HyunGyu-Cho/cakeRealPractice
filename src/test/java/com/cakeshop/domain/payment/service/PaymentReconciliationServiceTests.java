package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.infra.PaymentSnapshot;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentReconciliationServiceTests {
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-07-27T05:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock PaymentMapper paymentMapper;
    @Mock PaymentGateway gateway;

    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService(paymentMapper, gateway, 30, CLOCK);
    }

    private Payment ready(String paymentKey) {
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setTossOrderId("ORD-1");
        payment.setPaymentKey(paymentKey);
        payment.setStatus("READY");
        return payment;
    }

    /** 결제창을 열지도 않고 떠난 준비 행. 제공자에게 물어볼 것이 없다. */
    @Test
    void expiresPreparedPaymentThatNeverReachedTheProvider() {
        when(paymentMapper.findStaleReady(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(ready(null)));
        when(paymentMapper.abortPayment(eq(500L), eq("EXPIRED"), any(), any(), any())).thenReturn(1);

        assertThat(service.reconcileStaleReady()).isEqualTo(1);

        verify(gateway, never()).find(any());
    }

    @Test
    void syncsOurStatusToWhatTheProviderActuallyHas() {
        when(paymentMapper.findStaleReady(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(ready("PK-1")));
        when(gateway.find("PK-1"))
            .thenReturn(new PaymentSnapshot("PK-1", "ORD-1", 82_000L, "ABORTED"));
        when(paymentMapper.syncStatus(500L, "READY", "ABORTED", "ABORTED")).thenReturn(1);

        assertThat(service.reconcileStaleReady()).isEqualTo(1);
    }

    /** 모의 결제처럼 대조할 외부 상태가 없으면 아무것도 바꾸지 않는다. */
    @Test
    void leavesPaymentUntouchedWhenProviderKnowsNothing() {
        when(paymentMapper.findStaleReady(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(ready("PK-1")));
        when(gateway.find("PK-1")).thenReturn(null);

        assertThat(service.reconcileStaleReady()).isZero();

        verify(paymentMapper, never()).syncStatus(any(), any(), any(), any());
        verify(paymentMapper, never()).abortPayment(any(), any(), any(), any(), any());
    }

    /** 한 건이 실패해도 나머지는 계속 처리한다. */
    @Test
    void continuesAfterOneFailure() {
        Payment second = ready("PK-2");
        second.setId(501L);
        when(paymentMapper.findStaleReady(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(ready("PK-1"), second));
        when(gateway.find("PK-1")).thenThrow(new IllegalStateException("타임아웃"));
        when(gateway.find("PK-2"))
            .thenReturn(new PaymentSnapshot("PK-2", "ORD-2", 1_000L, "EXPIRED"));
        when(paymentMapper.syncStatus(501L, "READY", "EXPIRED", "EXPIRED")).thenReturn(1);

        assertThat(service.reconcileStaleReady()).isEqualTo(1);
    }
}
