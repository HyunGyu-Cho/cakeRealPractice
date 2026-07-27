package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.WebhookEvent;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.payment.mapper.WebhookEventMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookEventProcessorTests {
    @Mock WebhookEventMapper webhookEventMapper;
    @Mock PaymentMapper paymentMapper;

    private WebhookEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WebhookEventProcessor(webhookEventMapper, paymentMapper);
    }

    private WebhookEvent event(String providerStatus) {
        WebhookEvent event = new WebhookEvent();
        event.setId(3L);
        event.setEventId("evt-1");
        event.setTossOrderId("ORD-1");
        event.setProviderStatus(providerStatus);
        return event;
    }

    private Payment payment(String status) {
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setTossOrderId("ORD-1");
        payment.setStatus(status);
        return payment;
    }

    @Test
    void appliesExternalCancellationToOurPayment() {
        when(paymentMapper.findByTossOrderId("ORD-1")).thenReturn(Optional.of(payment("DONE")));
        when(paymentMapper.syncStatus(500L, "DONE", "CANCELED", "CANCELED")).thenReturn(1);

        assertThat(processor.process(event("CANCELED"))).isTrue();

        verify(webhookEventMapper).markProcessed(3L, "PROCESSED", null);
    }

    /** 승인 결과는 확정 단계가 이미 반영했다. 웹훅이 늦게 와도 바꿀 것이 없다. */
    @Test
    void skipsWhenOurStatusIsAlreadyUpToDate() {
        when(paymentMapper.findByTossOrderId("ORD-1")).thenReturn(Optional.of(payment("DONE")));

        assertThat(processor.process(event("DONE"))).isFalse();

        verify(paymentMapper, never()).syncStatus(any(), any(), any(), any());
        verify(webhookEventMapper).markProcessed(3L, "SKIPPED", null);
    }

    /** 모르는 상태를 실패로 남기면 계속 재처리된다. 건너뛴 것으로 마감한다. */
    @Test
    void skipsUnknownProviderStatus() {
        assertThat(processor.process(event("SOMETHING_NEW"))).isFalse();

        verify(webhookEventMapper).markProcessed(eq(3L), eq("SKIPPED"), isNull());
    }

    @Test
    void skipsWhenPaymentIsUnknownToUs() {
        when(paymentMapper.findByTossOrderId("ORD-1")).thenReturn(Optional.empty());

        assertThat(processor.process(event("CANCELED"))).isFalse();

        verify(webhookEventMapper).markProcessed(eq(3L), eq("SKIPPED"), isNull());
    }
}
