package com.cakeshop.domain.home.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.community.dto.view.PostSummaryView;
import com.cakeshop.domain.coupon.dto.view.DownloadableCouponView;
import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.product.dto.view.CategorySummaryView;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.review.dto.view.BestReviewView;
import com.cakeshop.domain.store.dto.view.StorePublicView;
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
 * 메인이 실제 Thymeleaf 엔진을 통과하는지 확인한다. 컨트롤러 단위 테스트는 뷰 이름만 보므로
 * 조합 섹션(쿠폰·후기·인기 글)의 표현식 오류를 놓친다. 데이터가 있을 때와 없을 때를 모두 그린다.
 */
@SpringBootTest
@ActiveProfiles("local")
class HomeScreenRenderingTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired private WebApplicationContext context;
    @MockitoBean private HomeService homeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(homeService.getStore()).thenReturn(new StorePublicView(
            "스위트온 케이크", "소개", "/uploads/store/photo.jpg", "서울시", "02-0000-0000",
            "평일 10:00 ~ 20:00", "일요일 휴무", "1층", "10:00 ~ 19:00"));
        when(homeService.getCategories()).thenReturn(List.of(
            new CategorySummaryView("GENERAL", "일반 케이크", "판매 중 3개", 3L)));
    }

    @Test
    void mainRendersEverySectionWithData() throws Exception {
        when(homeService.getPopularProducts()).thenReturn(List.of(product()));
        when(homeService.getDownloadableCoupons(7L)).thenReturn(List.of(coupon()));
        when(homeService.getBestReviews()).thenReturn(List.of(review()));
        when(homeService.getPopularPosts()).thenReturn(List.of(post()));

        mockMvc.perform(get("/").with(user(customer())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(
                allOf(
                    containsString("받을 수 있는 쿠폰"),
                    containsString("초코 가나슈 케이크"),
                    containsString("케이크러버"))));
    }

    @Test
    void mainRendersEmptyStatesForGuest() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                allOf(
                    containsString("아직 등록된 후기가 없습니다."),
                    containsString("아직 등록된 게시글이 없습니다."),
                    // 비로그인은 쿠폰 섹션 자체가 없다
                    not(
                        containsString("id=\"home-coupons\"")))));
    }

    private ProductSummaryView product() {
        return new ProductSummaryView(5L, "딸기 생크림 케이크", 35_000L, "GENERAL", "일반 케이크",
            "ACTIVE", "판매 중", 10, "재고 있음", false, "/uploads/product/cake.jpg", NOW);
    }

    private DownloadableCouponView coupon() {
        return new DownloadableCouponView(3L, "가입 축하 쿠폰", "3,000원 할인", 0L,
            NOW.plusDays(10), 40, false, true, null);
    }

    private BestReviewView review() {
        return new BestReviewView(100L, 8L, "초코 가나슈 케이크", "케이크러버", 5,
            "맛있게 잘 먹었습니다.", "/uploads/review/first.jpg", NOW.minusDays(1));
    }

    private PostSummaryView post() {
        return new PostSummaryView(1L, "FREE", "자유", "케이크 후기", "케이크러버", 12L, 3L, "2026.07.25");
    }

    private MemberDetails customer() {
        return new MemberDetails(7L, "a@b.c", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
