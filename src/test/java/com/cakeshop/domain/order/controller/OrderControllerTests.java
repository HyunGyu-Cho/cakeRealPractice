package com.cakeshop.domain.order.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.service.RefundService;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrderControllerTests {
    @Mock OrderService orderService;
    @Mock RefundService refundService;
    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new OrderController(orderService, refundService)).build();
        MemberDetails member = new MemberDetails(
            1L, "user@test.local", "password",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication =
            new UsernamePasswordAuthenticationToken(member, member.getPassword(), member.getAuthorities());
    }

    @Test
    void detailUsesAuthenticatedMemberOwnershipAndRendersModel() throws Exception {
        OrderDetailView detail = org.mockito.Mockito.mock(OrderDetailView.class);
        when(orderService.getOwnedOrder(1L, 9L)).thenReturn(detail);

        mockMvc.perform(get("/orders/9").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/detail"))
            .andExpect(model().attribute("order", detail))
            .andExpect(model().attributeExists("cancelForm"));
    }

    @Test
    void validCancellationUsesPrgAndFlashMessage() throws Exception {
        mockMvc.perform(post("/orders/9/cancel")
                .principal(authentication)
                .param("reason", "일정 변경"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/orders/9"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(refundService).cancelByCustomer(1L, 9L, "일정 변경");
    }

    @Test
    void blankCancellationReasonDoesNotCallService() throws Exception {
        mockMvc.perform(post("/orders/9/cancel")
                .principal(authentication)
                .param("reason", " "))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/orders/9"))
            .andExpect(flash().attributeExists("errorMessage"));

        verify(refundService, never()).cancelByCustomer(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }
}
