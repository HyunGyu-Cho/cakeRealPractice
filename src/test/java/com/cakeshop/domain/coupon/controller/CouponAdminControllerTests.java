package com.cakeshop.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.coupon.dto.form.CouponForm;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CouponAdminControllerTests {

    private MockMvc mockMvc;
    private CouponAdminService couponAdminService;

    @BeforeEach
    void setUp() {
        couponAdminService = mock(CouponAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponAdminController(couponAdminService))
            .setCustomArgumentResolvers(
                new org.springframework.security.web.method.annotation
                    .AuthenticationPrincipalArgumentResolver())
            .build();

        MemberDetails admin = new MemberDetails(3L, "admin@cakeshop.local", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(admin, "pw", admin.getAuthorities()));
    }

    @Test
    void createRedirectsAndRecordsTheLoggedInAdmin() throws Exception {
        mockMvc.perform(post("/admin/coupons")
                .param("name", "여름 쿠폰")
                .param("discountType", "PERCENTAGE")
                .param("discountValue", "10")
                .param("minimumOrderAmount", "20000")
                .param("maximumDiscountAmount", "5000")
                .param("totalQuantity", "100")
                .param("startsAt", "2026-08-01T00:00")
                .param("expiresAt", "2026-09-01T00:00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"));

        verify(couponAdminService).createCoupon(any(CouponForm.class), eq(3L));
    }

    @Test
    void invalidFormReRendersWithoutRedirecting() throws Exception {
        // 만료가 시작보다 이르다 — @AssertTrue 교차 검증에 걸려 폼을 다시 그린다
        mockMvc.perform(post("/admin/coupons")
                .param("name", "잘못된 쿠폰")
                .param("discountType", "PERCENTAGE")
                .param("discountValue", "10")
                .param("minimumOrderAmount", "0")
                .param("totalQuantity", "100")
                .param("startsAt", "2026-09-01T00:00")
                .param("expiresAt", "2026-08-01T00:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/coupon/form"))
            .andExpect(model().attributeHasFieldErrors("couponForm", "periodValid"));

        verify(couponAdminService, never()).createCoupon(any(), any());
    }

    @Test
    void percentageOver100IsRejected() throws Exception {
        mockMvc.perform(post("/admin/coupons")
                .param("name", "과한 쿠폰")
                .param("discountType", "PERCENTAGE")
                .param("discountValue", "150")
                .param("minimumOrderAmount", "0")
                .param("totalQuantity", "10")
                .param("startsAt", "2026-08-01T00:00")
                .param("expiresAt", "2026-09-01T00:00"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("couponForm", "percentageInRange"));
    }

    @Test
    void statusChangeDelegatesToTheService() throws Exception {
        mockMvc.perform(post("/admin/coupons/11/status").param("status", "SUSPENDED"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"));

        verify(couponAdminService).changeStatus(11L, CouponStatus.SUSPENDED);
    }

    @Test
    void issueRedirectsOnSuccess() throws Exception {
        mockMvc.perform(post("/admin/coupons/11/issue").param("memberId", "5"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"));

        verify(couponAdminService).issueToMember(11L, 5L);
    }
}
