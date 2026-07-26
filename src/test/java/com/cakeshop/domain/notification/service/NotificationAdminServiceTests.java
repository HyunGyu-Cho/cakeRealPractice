package com.cakeshop.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.dto.form.NotificationSearchForm;
import com.cakeshop.domain.notification.dto.view.AdminNotificationView;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationAdminServiceTests {

    @Mock private NotificationMapper notificationMapper;
    @Mock private MemberService memberService;

    private NotificationAdminService notificationAdminService;

    @BeforeEach
    void setUp() {
        notificationAdminService =
            new NotificationAdminService(notificationMapper, memberService);
    }

    @Test
    void pageFillsReceiverNicknameThroughMemberServiceContract() {
        when(notificationMapper.countBySearch(isNull(), isNull(), isNull())).thenReturn(1L);
        when(notificationMapper.findSearchPage(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of(notification(1L)));
        when(memberService.getProfileMap(any())).thenReturn(Map.of(
            1L, new MemberProfileView(1L, "홍길동", "a@b.com", null, LocalDateTime.now())));

        PageResult<AdminNotificationView> page = notificationAdminService.getPage(
            new NotificationSearchForm(), new PageRequest(1, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).receiverNickname()).isEqualTo("홍길동");
        assertThat(page.getContent().get(0).typeLabel()).isEqualTo("결제 완료");
    }

    @Test
    void emptyResultSkipsRowAndProfileQueries() {
        when(notificationMapper.countBySearch(any(), any(), isNull())).thenReturn(0L);

        PageResult<AdminNotificationView> page = notificationAdminService.getPage(
            new NotificationSearchForm(), new PageRequest(1, 20));

        assertThat(page.getContent()).isEmpty();
        verify(notificationMapper, never()).findSearchPage(any(), any(), any(), anyInt(), anyInt());
        verify(memberService, never()).getProfileMap(any());
    }

    private Notification notification(Long receiverId) {
        Notification notification = new Notification();
        notification.setId(50L);
        notification.setReceiverId(receiverId);
        notification.setNotificationType(NotificationType.ORDER_PAID);
        notification.setTitle("결제 완료");
        notification.setContent("결제가 완료되었습니다.");
        notification.setOrderId(9L);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
