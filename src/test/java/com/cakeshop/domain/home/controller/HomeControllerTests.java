package com.cakeshop.domain.home.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.coupon.dto.view.DownloadableCouponView;
import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerTests {

    private static final Long MEMBER_ID = 7L;

    private MockMvc mockMvc;
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = mock(HomeService.class);
        when(homeService.getStore()).thenReturn(new StorePublicView(
            "스위트온 케이크", "소개", "/uploads/store/202607/photo.jpg", "서울시", "02-0000-0000",
            "평일 10:00 ~ 20:00", "일요일 휴무", "1층", "10:00 ~ 19:00"
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(homeService))
            .setCustomArgumentResolvers(
                new org.springframework.security.web.method.annotation
                    .AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void homeExposesEveryComposedSection() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/main"))
            .andExpect(model().attributeExists(
                "store", "categories", "popularProducts", "downloadableCoupons",
                "bestReviews", "popularPosts"));
    }

    @Test
    void guestAsksCouponsWithoutMemberId() throws Exception {
        // 메인은 비로그인도 열리는 화면 — principal이 null이어도 깨지지 않아야 한다
        mockMvc.perform(get("/")).andExpect(status().isOk());

        verify(homeService).getDownloadableCoupons(isNull());
    }

    @Test
    void loggedInMemberGetsDownloadableCoupons() throws Exception {
        login();
        when(homeService.getDownloadableCoupons(MEMBER_ID)).thenReturn(List.of(coupon()));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("downloadableCoupons", List.of(coupon())));
    }

    private void login() {
        MemberDetails member = new MemberDetails(MEMBER_ID, "a@b.c", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(member, "pw", member.getAuthorities()));
    }

    private DownloadableCouponView coupon() {
        return new DownloadableCouponView(1L, "가입 축하 쿠폰", "3,000원 할인", 20000L,
            LocalDateTime.of(2026, 8, 31, 23, 59), 10, false, true, null);
    }
}
