package com.cakeshop.domain.coupon.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.coupon.dto.view.MyCouponView;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CouponControllerTests {

    private MockMvc mockMvc;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = mock(CouponService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponController(couponService))
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
    void couponBoxGroupsByDerivedState() throws Exception {
        when(couponService.getMyCoupons(7L)).thenReturn(List.of(
            coupon(1L, MyCouponView.AVAILABLE),
            coupon(2L, MyCouponView.USED),
            coupon(3L, MyCouponView.EXPIRED)));

        mockMvc.perform(get("/mypage/coupons"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/coupon/list"))
            .andExpect(model().attributeExists("availableCoupons", "usedCoupons", "expiredCoupons"));
    }

    @Test
    void downloadRedirectsWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/coupons/9/download"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/coupons"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).download(7L, 9L);
    }

    @Test
    void downloadFailureIsShownAsAnErrorMessageInsteadOfAnErrorPage() throws Exception {
        Mockito.doThrow(new BusinessException(CouponErrorCode.SOLD_OUT))
            .when(couponService).download(7L, 9L);

        mockMvc.perform(post("/coupons/9/download"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/coupons"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    private MyCouponView coupon(Long id, String state) {
        return new MyCouponView(id, "쿠폰", "3,000원 할인", 0L,
            LocalDateTime.of(2026, 12, 31, 0, 0), null, state, state);
    }
}
