package com.cakeshop.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.notification.dto.form.NotificationSearchForm;
import com.cakeshop.domain.notification.dto.view.NotificationSliceView;
import com.cakeshop.domain.notification.service.NotificationAdminService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationAdminControllerTests {

    @Mock private NotificationService notificationService;
    @Mock private NotificationAdminService notificationAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MemberDetails principal = new MemberDetails(
            2L, "admin@cakeshop.local", "password",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new NotificationAdminController(notificationService, notificationAdminService))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pageShowsOwnNotificationsAndFilteredHistory() throws Exception {
        when(notificationService.getSlice(2L, null, 20)).thenReturn(
            new NotificationSliceView(List.of(), false, null, 0));
        when(notificationAdminService.getPage(any(NotificationSearchForm.class),
            any(PageRequest.class))).thenReturn(
                new PageResult<>(List.of(), new PageRequest(1, 20), 0));

        mockMvc.perform(get("/admin/notifications").param("type", "ORDER_PAID").param("read", "UNREAD"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/notification/list"))
            .andExpect(model().attributeExists("mine", "notifications", "types"));

        ArgumentCaptor<NotificationSearchForm> cond =
            ArgumentCaptor.forClass(NotificationSearchForm.class);
        verify(notificationAdminService).getPage(cond.capture(), any(PageRequest.class));
        org.assertj.core.api.Assertions.assertThat(cond.getValue().normalizedType().name())
            .isEqualTo("ORDER_PAID");
        org.assertj.core.api.Assertions.assertThat(cond.getValue().normalizedRead()).isFalse();
    }

    @Test
    void unknownFilterValuesFallBackToNoFilter() {
        NotificationSearchForm cond = new NotificationSearchForm();
        cond.setType("NOT_A_TYPE");
        cond.setRead("이상한값");

        org.assertj.core.api.Assertions.assertThat(cond.normalizedType()).isNull();
        org.assertj.core.api.Assertions.assertThat(cond.normalizedRead()).isNull();
    }
}
