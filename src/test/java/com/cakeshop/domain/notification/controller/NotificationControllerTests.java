package com.cakeshop.domain.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.notification.dto.view.NotificationSliceView;
import com.cakeshop.domain.notification.dto.view.NotificationView;
import com.cakeshop.domain.notification.error.NotificationErrorCode;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.error.ApiExceptionHandler;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.cakeshop.global.security.MemberDetails;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTests {

    @Mock private NotificationService notificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MemberDetails principal = new MemberDetails(
            7L, "customer@example.com", "password",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new NotificationController(notificationService),
                new NotificationApiController(notificationService))
            // JSON 오류 응답은 도메인 전용 advice가 아니라 공통 핸들러가 담당한다.
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // 같은 스레드를 쓰는 뒤 테스트(@SpringBootTest 보안 테스트)에 인증이 새지 않도록 비운다.
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPageRendersLoginMembersOwnSlice() throws Exception {
        when(notificationService.getSlice(7L, null, 20)).thenReturn(slice());

        mockMvc.perform(get("/notifications"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/notification/list"))
            .andExpect(model().attributeExists("slice"));

        verify(notificationService).getSlice(7L, null, 20);
    }

    @Test
    void sliceApiReturnsUnreadCountAndContent() throws Exception {
        when(notificationService.getSlice(7L, null, 20)).thenReturn(slice());

        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(1))
            .andExpect(jsonPath("$.content[0].typeLabel").value("결제 완료"));
    }

    @Test
    void readEndpointsReturnNoContent() throws Exception {
        mockMvc.perform(post("/api/notifications/100/read"))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/notifications/read-all"))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(100L, 7L);
        verify(notificationService).markAllRead(7L);
    }

    @Test
    void readingOtherMembersNotificationIsForbidden() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(NotificationErrorCode.FORBIDDEN))
            .when(notificationService).markRead(100L, 7L);

        mockMvc.perform(post("/api/notifications/100/read"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_002"));
    }

    private NotificationSliceView slice() {
        return new NotificationSliceView(
            List.of(new NotificationView(100L, "ORDER_PAID", "결제 완료", "결제 완료",
                "결제가 완료되었습니다.", false, "/orders/9", LocalDateTime.now())),
            false, 100L, 1L);
    }
}
