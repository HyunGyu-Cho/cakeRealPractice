package com.cakeshop.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.notification.dto.view.NotificationDeliveryRetryRow;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.event.NotificationEvent;
import com.cakeshop.domain.notification.event.NotificationPusher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliverySchedulerTests {

    @Mock private NotificationDeliveryService deliveryService;
    @Mock private NotificationPusher pusher;

    private NotificationDeliveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationDeliveryScheduler(deliveryService, pusher);
    }

    @Test
    void doesNothingWhenNoTargetExists() {
        when(deliveryService.findRetryTargets(anyInt())).thenReturn(List.of());

        scheduler.retryFailedDeliveries();

        verifyNoInteractions(pusher);
        verify(deliveryService, never()).markSent(any(), any(), any());
    }

    @Test
    void successfulRetryIsRecordedAsSentFromItsCurrentStatus() {
        when(deliveryService.findRetryTargets(anyInt()))
            .thenReturn(List.of(target(500L, DeliveryStatus.FAILED, 1)));
        when(pusher.pushToMember(any(NotificationEvent.class))).thenReturn("customer@example.com");

        scheduler.retryFailedDeliveries();

        verify(deliveryService).markSent(500L, DeliveryStatus.FAILED, "customer@example.com");
    }

    @Test
    void failedRetryIncrementsRetryCount() {
        when(deliveryService.findRetryTargets(anyInt()))
            .thenReturn(List.of(target(500L, DeliveryStatus.FAILED, 1)));
        when(pusher.pushToMember(any(NotificationEvent.class)))
            .thenThrow(new IllegalStateException("broker down"));

        scheduler.retryFailedDeliveries();

        verify(deliveryService).markRetryFailure(500L, DeliveryStatus.FAILED, 1,
            "IllegalStateException", "broker down");
    }

    /** 한 건이 실패해도 남은 배치는 계속 처리한다. */
    @Test
    void oneFailingRowDoesNotStopTheBatch() {
        when(deliveryService.findRetryTargets(anyInt())).thenReturn(List.of(
            target(500L, DeliveryStatus.FAILED, 1),
            target(501L, DeliveryStatus.REQUESTED, 0)));
        when(pusher.pushToMember(any(NotificationEvent.class)))
            .thenThrow(new IllegalStateException("broker down"))
            .thenReturn("customer@example.com");

        scheduler.retryFailedDeliveries();

        verify(pusher, times(2)).pushToMember(any(NotificationEvent.class));
        verify(deliveryService).markSent(501L, DeliveryStatus.REQUESTED, "customer@example.com");
    }

    @Test
    void failureRecordingErrorDoesNotStopTheBatch() {
        when(deliveryService.findRetryTargets(anyInt())).thenReturn(List.of(
            target(500L, DeliveryStatus.FAILED, 1),
            target(501L, DeliveryStatus.REQUESTED, 0)));
        when(pusher.pushToMember(any(NotificationEvent.class)))
            .thenThrow(new IllegalStateException("broker down"));
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
            .when(deliveryService).markRetryFailure(any(), any(), anyInt(), any(), any());

        assertThatCode(() -> scheduler.retryFailedDeliveries()).doesNotThrowAnyException();

        verify(pusher, times(2)).pushToMember(any(NotificationEvent.class));
    }

    private NotificationDeliveryRetryRow target(Long deliveryId, DeliveryStatus status,
                                                int retryCount) {
        return new NotificationDeliveryRetryRow(deliveryId, status, retryCount, 100L, 1L,
            NotificationType.ORDER_PAID, "결제 완료", "결제가 완료되었습니다.", false,
            "/orders/9", LocalDateTime.now());
    }
}
