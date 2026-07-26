package com.cakeshop.domain.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.service.CustomOrderService;
import com.cakeshop.domain.payment.service.CustomOrderPaymentService;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomOrderControllerTests {

    private MockMvc mockMvc;
    private CustomOrderService customOrderService;
    private CustomOrderPaymentService customOrderPaymentService;

    @BeforeEach
    void setUp() {
        customOrderService = mock(CustomOrderService.class);
        customOrderPaymentService = mock(CustomOrderPaymentService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new CustomOrderController(customOrderService, customOrderPaymentService))
            .setCustomArgumentResolvers(
                new org.springframework.security.web.method.annotation
                    .AuthenticationPrincipalArgumentResolver())
            .build();

        MemberDetails member = new MemberDetails(7L, "a@b.c", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(member, "pw", member.getAuthorities()));
    }

    @Test
    void optionsPageProvidesProductAndOptionGroups() throws Exception {
        when(customOrderService.findDefaultCustomProduct()).thenReturn(Optional.of(product()));
        when(customOrderService.getOptionGroups(3L)).thenReturn(List.of());

        mockMvc.perform(get("/orders/custom/options"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-option"))
            .andExpect(model().attributeExists("product", "optionGroups", "customOrderForm"));
    }

    @Test
    void submitRedirectsToDetailOnSuccess() throws Exception {
        when(customOrderService.submitRequest(eq(7L), any())).thenReturn(100L);

        mockMvc.perform(post("/orders/custom/options")
                .param("productId", "3")
                .param("optionIds", "11")
                .param("pickupAt", "2026-12-01T14:00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/orders/custom/100"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void submitRerendersFormWithoutRedirectOnValidationError() throws Exception {
        when(customOrderService.findDefaultCustomProduct()).thenReturn(Optional.of(product()));
        when(customOrderService.getCustomProduct(3L)).thenReturn(product());

        // pickupAt 누락 → @NotNull 위반. 검증 실패는 리다이렉트 없이 폼을 다시 그린다.
        mockMvc.perform(post("/orders/custom/options").param("productId", "3"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-option"));
        verify(customOrderService, never()).submitRequest(anyLong(), any());
    }

    @Test
    void submitPutsBusinessErrorBackOnTheForm() throws Exception {
        when(customOrderService.getCustomProduct(3L)).thenReturn(product());
        when(customOrderService.submitRequest(eq(7L), any()))
            .thenThrow(new BusinessException(CustomOrderErrorCode.OPTION_REQUIRED));

        mockMvc.perform(post("/orders/custom/options")
                .param("productId", "3")
                .param("pickupAt", "2026-12-01T14:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-option"))
            .andExpect(model().attributeHasErrors("customOrderForm"));
    }

    @Test
    void requestEntryRedirectsToLatestRequest() throws Exception {
        when(customOrderService.findMyLatestRequestId(7L)).thenReturn(Optional.of(42L));

        mockMvc.perform(get("/orders/custom/request"))
            .andExpect(redirectedUrl("/orders/custom/42"));
    }

    @Test
    void requestEntryFallsBackToOptionsWhenNoRequestYet() throws Exception {
        when(customOrderService.findMyLatestRequestId(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/custom/request"))
            .andExpect(redirectedUrl("/orders/custom/options"));
    }

    @Test
    void acceptRedirectsToIssuedPaymentLink() throws Exception {
        when(customOrderService.acceptQuote(7L, 100L)).thenReturn("tok-xyz");

        mockMvc.perform(post("/orders/custom/100/accept"))
            .andExpect(redirectedUrl("/orders/custom/pay/tok-xyz"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void payRedirectsToOrderDetail() throws Exception {
        when(customOrderPaymentService.pay("tok-xyz", 7L, "CARD")).thenReturn(100L);

        mockMvc.perform(post("/orders/custom/pay/tok-xyz").param("method", "CARD"))
            .andExpect(redirectedUrl("/orders/100"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void cancelRedirectsBackToRequest() throws Exception {
        mockMvc.perform(post("/orders/custom/100/cancel").param("reason", "일정 변경"))
            .andExpect(redirectedUrl("/orders/custom/100"));
        verify(customOrderService).cancelRequest(7L, 100L, "일정 변경");
    }

    private ProductDetailView product() {
        return new ProductDetailView(3L, "레터링 주문 케이크", "설명", 55_000L,
            "CUSTOM", "주문 제작", "ACTIVE", "판매 중", null, "주문 가능",
            5, 3, null, new BigDecimal("0.00"), 0, true);
    }
}
