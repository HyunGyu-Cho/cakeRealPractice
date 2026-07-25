package com.cakeshop.domain.payment.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.service.PaymentFacade;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTests {
    @Mock PaymentFacade paymentFacade;
    @Mock OrderService orderService;
    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken authentication;
    private CheckoutDraft draft;
    private CheckoutView checkout;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new PaymentController(paymentFacade, orderService)).build();
        MemberDetails member = new MemberDetails(
            1L, "user@test.local", "password",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication =
            new UsernamePasswordAuthenticationToken(member, member.getPassword(), member.getAuthorities());
        draft = new CheckoutDraft(1L, List.of(11L));
        draft.setPickupAt(LocalDateTime.of(2026, 7, 27, 14, 0));
        checkout = new CheckoutView(
            draft.getCheckoutId(), List.of(), 82_000L, 2, draft.getPickupAt());
        org.mockito.Mockito.lenient()
            .when(orderService.getCheckoutView(1L, draft)).thenReturn(checkout);
    }

    @Test
    void paymentFormRendersServerCheckoutModel() throws Exception {
        MockHttpSession session = session();

        mockMvc.perform(get("/orders/payment").principal(authentication).session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/payment/form"))
            .andExpect(model().attribute("checkout", checkout))
            .andExpect(model().attributeExists("paymentForm"));

        verify(orderService).validateReadyForPayment(1L, draft);
    }

    @Test
    void successfulPaymentUsesPrgAndClearsDraft() throws Exception {
        MockHttpSession session = session();
        when(paymentFacade.pay(1L, draft.getCheckoutId(), draft, "CARD")).thenReturn(99L);

        mockMvc.perform(post("/orders/payment")
                .principal(authentication).session(session)
                .param("checkoutId", draft.getCheckoutId())
                .param("method", "CARD")
                .param("refundPolicyAgreed", "true")
                .param("amount", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/orders/complete?orderId=99"));

        verify(paymentFacade).pay(1L, draft.getCheckoutId(), draft, "CARD");
        org.assertj.core.api.Assertions.assertThat(
            session.getAttribute(CheckoutDraft.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void missingPolicyAgreementRendersFormWithoutPayment() throws Exception {
        mockMvc.perform(post("/orders/payment")
                .principal(authentication).session(session())
                .param("checkoutId", draft.getCheckoutId())
                .param("method", "CARD"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/payment/form"))
            .andExpect(model().attributeHasFieldErrors("paymentForm", "refundPolicyAgreed"));

        verify(paymentFacade, never()).pay(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CheckoutDraft.SESSION_ATTRIBUTE, draft);
        return session;
    }
}
