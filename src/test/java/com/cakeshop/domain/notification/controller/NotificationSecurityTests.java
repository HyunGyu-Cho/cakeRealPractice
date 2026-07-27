package com.cakeshop.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class NotificationSecurityTests {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                .springSecurity())
            .build();
    }

    @Test
    void anonymousNotificationsRedirectToLoginAndSaveReturnPath() throws Exception {
        MvcResult result = mockMvc.perform(get("/notifications"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andReturn();

        DefaultSavedRequest saved = (DefaultSavedRequest) result.getRequest().getSession()
            .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        assertThat(saved.getRedirectUrl()).contains("/notifications");
    }

    @Test
    void ordinaryMemberCannotOpenAdminNotificationPage() throws Exception {
        mockMvc.perform(get("/admin/notifications").with(user("member").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void notificationApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }
}
