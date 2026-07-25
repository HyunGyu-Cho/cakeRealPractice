package com.cakeshop.domain.chat.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.chat.dto.view.ChatRoomDetailView;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
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

@ExtendWith(MockitoExtension.class)
class ChatControllerTests {

    @Mock private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MemberDetails principal = new MemberDetails(
            7L, "customer@example.com", "password",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @Test
    void customerPageRendersRealChatModel() throws Exception {
        when(chatService.getCustomerRoom(7L)).thenReturn(new ChatRoomDetailView(
            null, 7L, "고객", "customer@example.com", "OPEN", "상담 중", List.of()));

        mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/chat/room"))
            .andExpect(model().attributeExists("chat"));

        verify(chatService).getCustomerRoom(7L);
    }
}
