package com.cakeshop.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.mockup.public-preview=true")
class ChatSecurityTests {

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
    void chatIsNoLongerPublicPreviewAndSavesLoginReturnPath() throws Exception {
        MvcResult result = mockMvc.perform(get("/chat"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andReturn();

        DefaultSavedRequest saved = (DefaultSavedRequest) result.getRequest().getSession()
            .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        assertThat(saved.getRedirectUrl()).contains("/chat");
    }

    @Test
    void ordinaryMemberCannotUseAdminChatPageOrApi() throws Exception {
        mockMvc.perform(get("/admin/chat").with(user("member").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/api/chat/rooms/1/close")
                .with(user("member").roles("USER"))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotUseCustomerMessageHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/chat/messages").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
