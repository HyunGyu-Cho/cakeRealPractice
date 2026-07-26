package com.cakeshop.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.dto.view.NotificationSliceView;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.error.NotificationErrorCode;
import com.cakeshop.domain.notification.event.NotificationEvent;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock private NotificationMapper notificationMapper;
    @Mock private MemberService memberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService =
            new NotificationService(notificationMapper, memberService, eventPublisher);
    }

    @Test
    void notifySavesOneRowAndPublishesEventForTheReceiver() {
        when(memberService.getProfile(1L)).thenReturn(
            new MemberProfileView(1L, "고객", "customer@example.com", null, LocalDateTime.now()));
        givenInsertAssignsId(100L);

        notificationService.notify(NotificationCommand.forOrder(
            1L, NotificationType.ORDER_PAID, 9L, "결제 완료", "결제가 완료되었습니다."));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(saved.capture());
        assertThat(saved.getValue().getReceiverId()).isEqualTo(1L);
        assertThat(saved.getValue().getNotificationType()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(saved.getValue().getTargetUrl()).isEqualTo("/orders/9");

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().adminBroadcast()).isFalse();
        assertThat(event.getValue().receiverUsername()).isEqualTo("customer@example.com");
        assertThat(event.getValue().notification().id()).isEqualTo(100L);
    }

    @Test
    void notifyRejectsCommandWithoutReceiver() {
        assertThatThrownBy(() -> notificationService.notify(NotificationCommand.toAdmins(
                NotificationType.ADMIN_ORDER_PLACED, "신규 주문", "내용", "/admin/orders", null, null)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(NotificationErrorCode.INVALID_RECEIVER));
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void notifyAdminsFansOutRowsButBroadcastsOnce() {
        when(memberService.findAdminMemberIds()).thenReturn(List.of(2L, 3L));

        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_CHAT_MESSAGE, "고객 문의", "새 메시지", "/admin/chat", null, 7L));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, org.mockito.Mockito.times(2)).insert(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getReceiverId)
            .containsExactly(2L, 3L);

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().adminBroadcast()).isTrue();
        // 수신자마다 id가 달라 브로드캐스트 payload에는 id를 담지 않는다.
        assertThat(event.getValue().notification().id()).isNull();
    }

    @Test
    void notifyAdminsSkipsSilentlyWhenNoAdminExists() {
        when(memberService.findAdminMemberIds()).thenReturn(List.of());

        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_ORDER_PLACED, "신규 주문", "내용", "/admin/orders", 9L, null));

        verify(notificationMapper, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void sliceTrimsToRequestedSizeAndExposesNextCursor() {
        List<Notification> rows = IntStream.rangeClosed(1, 3)
            .mapToObj(index -> notification((long) (10 - index), 1L))
            .toList();
        when(notificationMapper.findSliceByReceiver(1L, null, 3)).thenReturn(rows);
        when(notificationMapper.countUnread(1L)).thenReturn(2L);

        NotificationSliceView slice = notificationService.getSlice(1L, null, 2);

        assertThat(slice.content()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.nextCursor()).isEqualTo(8L);
        assertThat(slice.unreadCount()).isEqualTo(2L);
    }

    @Test
    void markReadRejectsOtherMembersNotification() {
        when(notificationMapper.findById(100L)).thenReturn(Optional.of(notification(100L, 2L)));

        assertThatThrownBy(() -> notificationService.markRead(100L, 1L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.FORBIDDEN));
        verify(notificationMapper, never()).markRead(any(), any());
    }

    @Test
    void markReadFailsWhenNotificationIsMissing() {
        when(notificationMapper.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(100L, 1L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.NOT_FOUND));
    }

    @Test
    void markReadOnAlreadyReadNotificationSucceeds() {
        Notification read = notification(100L, 1L);
        read.setRead(true);
        when(notificationMapper.findById(100L)).thenReturn(Optional.of(read));

        notificationService.markRead(100L, 1L);

        verify(notificationMapper).markRead(100L, 1L);
    }

    private void givenInsertAssignsId(Long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(id);
            return 1;
        }).when(notificationMapper).insert(any(Notification.class));
    }

    private Notification notification(Long id, Long receiverId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setReceiverId(receiverId);
        notification.setNotificationType(NotificationType.ORDER_PAID);
        notification.setTitle("결제 완료");
        notification.setContent("결제가 완료되었습니다.");
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
