package com.cakeshop.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.notification.dto.view.NotificationView;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationDeliveryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationBroadcasterTests {

    @Mock private NotificationPusher pusher;
    @Mock private NotificationDeliveryService deliveryService;

    private NotificationBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new NotificationBroadcaster(pusher, deliveryService);
    }

    @Test
    void successfulPushIsRecordedAsSent() {
        NotificationEvent event = memberEvent();
        when(pusher.pushToMember(event)).thenReturn("customer@example.com");

        broadcaster.broadcast(event);

        verify(deliveryService).markSent(500L, DeliveryStatus.REQUESTED, "customer@example.com");
    }

    /** 회원 조회 실패든 브로커 장애든 커밋된 알림을 되돌리지 않고 실패로 기록한다. */
    @Test
    void pushFailureIsRecordedAndNotRethrown() {
        NotificationEvent event = memberEvent();
        when(pusher.pushToMember(event)).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> broadcaster.broadcast(event)).doesNotThrowAnyException();

        verify(deliveryService).markInitialFailure(500L, DeliveryStatus.REQUESTED,
            "IllegalStateException", "broker down");
        verify(deliveryService, never()).markSent(any(), any(), any());
    }

    /** 실패 기록까지 실패하면 행이 REQUESTED로 남아 스케줄러가 집어간다. */
    @Test
    void failureRecordingErrorIsSwallowed() {
        NotificationEvent event = memberEvent();
        when(pusher.pushToMember(event)).thenThrow(new IllegalStateException("broker down"));
        doThrow(new IllegalStateException("db down"))
            .when(deliveryService).markInitialFailure(any(), any(), any(), any());

        assertThatCode(() -> broadcaster.broadcast(event)).doesNotThrowAnyException();
    }

    /** 관리자 알림도 같은 경로를 탄다 — 전달 이력과 재시도가 동일하게 걸린다. */
    @Test
    void adminNotificationTakesTheSamePersonalQueuePath() {
        NotificationEvent event = NotificationEvent.toMember(2L, 501L, new NotificationView(
            101L, NotificationType.ADMIN_ORDER_PLACED.name(),
            NotificationType.ADMIN_ORDER_PLACED.label(), "신규 주문", "내용", false,
            "/admin/orders", LocalDateTime.now()));
        when(pusher.pushToMember(event)).thenReturn("admin@cakeshop.local");

        broadcaster.broadcast(event);

        verify(deliveryService).markSent(501L, DeliveryStatus.REQUESTED, "admin@cakeshop.local");
    }

    private NotificationEvent memberEvent() {
        return NotificationEvent.toMember(1L, 500L, new NotificationView(
            100L, NotificationType.ORDER_PAID.name(), NotificationType.ORDER_PAID.label(),
            "결제 완료", "결제가 완료되었습니다.", false, "/orders/9", LocalDateTime.now()));
    }
}
