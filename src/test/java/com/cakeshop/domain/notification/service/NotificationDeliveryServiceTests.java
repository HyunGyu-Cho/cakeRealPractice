package com.cakeshop.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.notification.entity.DeliveryChannel;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationDelivery;
import com.cakeshop.domain.notification.error.NotificationErrorCode;
import com.cakeshop.domain.notification.mapper.NotificationDeliveryMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTests {

    @Mock private NotificationDeliveryMapper deliveryMapper;

    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new NotificationDeliveryService(deliveryMapper);
    }

    @Test
    void enqueueInsertsRequestedRowWithFirstRetryTime() {
        when(deliveryMapper.insert(any(NotificationDelivery.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, NotificationDelivery.class).setId(500L);
            return 1;
        });

        Long deliveryId = deliveryService.enqueue(100L);

        assertThat(deliveryId).isEqualTo(500L);
        ArgumentCaptor<NotificationDelivery> saved =
            ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryMapper).insert(saved.capture());
        assertThat(saved.getValue().getNotificationId()).isEqualTo(100L);
        assertThat(saved.getValue().getChannel()).isEqualTo(DeliveryChannel.WEBSOCKET);
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        // 커밋 직후 즉시 푸시가 유실돼도 스케줄러가 집어가도록 미리 채운다.
        assertThat(saved.getValue().getNextRetryAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void initialFailureKeepsRetryBudgetAndSchedulesFirstRetry() {
        deliveryService.markInitialFailure(500L, DeliveryStatus.REQUESTED, "IllegalStateException",
            "broker down");

        verify(deliveryMapper).markFailed(eq(500L), eq(DeliveryStatus.FAILED), eq(0),
            eq("IllegalStateException"), eq("broker down"), any(LocalDateTime.class),
            eq(DeliveryStatus.REQUESTED));
    }

    @Test
    void retryFailureBacksOffFurtherEachTime() {
        deliveryService.markRetryFailure(500L, DeliveryStatus.FAILED, 0, "code", "reason");
        deliveryService.markRetryFailure(500L, DeliveryStatus.FAILED, 1, "code", "reason");

        ArgumentCaptor<LocalDateTime> nextRetryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryMapper, org.mockito.Mockito.times(2)).markFailed(any(), any(), anyInt(),
            any(), any(), nextRetryAt.capture(), any());
        assertThat(nextRetryAt.getAllValues().get(1))
            .isAfter(nextRetryAt.getAllValues().get(0));
    }

    @Test
    void retryFailureAbandonsWhenBudgetIsExhausted() {
        deliveryService.markRetryFailure(500L, DeliveryStatus.FAILED,
            NotificationDeliveryService.MAX_RETRY_COUNT - 1, "code", "reason");

        verify(deliveryMapper).markFailed(eq(500L), eq(DeliveryStatus.ABANDONED),
            eq(NotificationDeliveryService.MAX_RETRY_COUNT), eq("code"), eq("reason"),
            isNull(), eq(DeliveryStatus.FAILED));
    }

    /** failure_reason 은 VARCHAR(500). 넘치면 UPDATE 가 매번 실패해 재시도가 무한 반복된다. */
    @Test
    void failureReasonIsTruncatedToColumnLength() {
        deliveryService.markInitialFailure(500L, DeliveryStatus.REQUESTED, "code",
            "x".repeat(700));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(deliveryMapper).markFailed(any(), any(), anyInt(), any(), reason.capture(),
            any(), any());
        assertThat(reason.getValue()).hasSize(500);
    }

    @Test
    void markSentRejectsTransitionFromFinalStatus() {
        assertThatThrownBy(() ->
            deliveryService.markSent(500L, DeliveryStatus.SENT, "customer@example.com"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(NotificationErrorCode.INVALID_DELIVERY_TRANSITION));
        verify(deliveryMapper, never()).markSent(any(), any(), any(), any());
    }

    /** 즉시 푸시와 스케줄러가 같은 행을 다투면 진 쪽은 조용히 넘어간다. */
    @Test
    void markSentSkipsQuietlyWhenAnotherPathAlreadyHandledTheRow() {
        when(deliveryMapper.markSent(500L, DeliveryStatus.SENT, "customer@example.com",
            DeliveryStatus.REQUESTED)).thenReturn(0);

        deliveryService.markSent(500L, DeliveryStatus.REQUESTED, "customer@example.com");
    }
}
