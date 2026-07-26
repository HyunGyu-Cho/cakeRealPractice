package com.cakeshop.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.coupon.dto.form.CouponForm;
import com.cakeshop.domain.coupon.dto.view.CouponAdminListView;
import com.cakeshop.domain.coupon.dto.view.DownloadableCouponView;
import com.cakeshop.domain.coupon.dto.view.MyCouponView;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 쿠폰 화면 4개가 실제 Thymeleaf 엔진을 통과하는지 확인한다.
 * 컨트롤러 단위 테스트(standalone MockMvc)는 뷰 이름만 검증해 템플릿 문법 오류를 놓친다.
 */
@SpringBootTest
@ActiveProfiles("local")
class CouponScreenRenderingTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired private WebApplicationContext context;
    @MockitoBean private CouponService couponService;
    @MockitoBean private CouponAdminService couponAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void customerCouponScreensRender() throws Exception {
        when(couponService.getMyCoupons(7L)).thenReturn(List.of(
            new MyCouponView(1L, "사용 가능 쿠폰", "3,000원 할인", 15_000L,
                NOW.plusDays(10), null, MyCouponView.AVAILABLE, "사용 가능"),
            new MyCouponView(2L, "사용한 쿠폰", "10% 할인 (최대 5,000원)", 0L,
                NOW.plusDays(10), NOW, MyCouponView.USED, "사용 완료")));
        when(couponService.getDownloadableCoupons(7L)).thenReturn(List.of(
            new DownloadableCouponView(3L, "받을 수 있는 쿠폰", "3,000원 할인", 0L,
                NOW.plusDays(10), 40, false, true, null),
            new DownloadableCouponView(4L, "이미 받은 쿠폰", "5% 할인", 10_000L,
                NOW.plusDays(10), 0, true, false, "이미 받은 쿠폰입니다.")));

        for (String path : List.of("/mypage/coupons", "/coupons")) {
            mockMvc.perform(get(path).with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void adminCouponScreensRender() throws Exception {
        when(couponAdminService.getCouponPage(any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(adminView()), new PageRequest(1, 10), 1));
        when(couponAdminService.getCoupon(11L)).thenReturn(adminView());
        when(couponAdminService.getCouponForm(11L)).thenReturn(new CouponForm());
        when(couponAdminService.searchIssueTargets(null)).thenReturn(List.of());

        for (String path : List.of("/admin/coupons", "/admin/coupons/new",
            "/admin/coupons/11/edit", "/admin/coupons/11/issue")) {
            mockMvc.perform(get(path).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    private CouponAdminListView adminView() {
        return new CouponAdminListView(11L, "여름 쿠폰", "PERCENTAGE", "정률 할인", 10L,
            "10% 할인 (최대 5,000원)", 20_000L, 5_000L, 100, 40,
            NOW.minusDays(1), NOW.plusDays(30), "ACTIVE", "발급 중", false, false);
    }

    private MemberDetails customer() {
        return new MemberDetails(7L, "a@b.c", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MemberDetails admin() {
        return new MemberDetails(3L, "admin@cakeshop.local", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
