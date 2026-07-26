package com.cakeshop.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.form.AdminReviewSearchForm;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.service.ReviewAdminService;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;
import java.math.BigDecimal;
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
 * 후기 화면이 실제 Thymeleaf 엔진을 통과하는지 확인한다.
 * 컨트롤러 단위 테스트(standalone MockMvc)는 뷰 이름만 검증해 템플릿 문법 오류를 놓친다.
 */
@SpringBootTest
@ActiveProfiles("local")
class ReviewScreenRenderingTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 10, 0);

    @Autowired private WebApplicationContext context;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private ReviewAdminService reviewAdminService;
    // 상품 상세는 product·review를 컨트롤러가 조합한다. 상품 쪽을 실제 서비스로 두면
    // 로컬 DB에 특정 id의 상품이 있어야만 통과하는 테스트가 된다.
    @MockitoBean private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void customerReviewScreensRender() throws Exception {
        when(reviewService.getReviewableItems(7L)).thenReturn(List.of(item()));
        when(reviewService.getMyReviews(7L)).thenReturn(List.of(myReview(false), myReview(true)));
        when(reviewService.getReviewTarget(7L, 30L)).thenReturn(item());
        when(reviewService.getReviewForEdit(7L, 100L)).thenReturn(new ReviewForm());
        when(reviewService.getImageUrls(100L)).thenReturn(List.of("/uploads/review/cake.jpg"));

        for (String path : List.of("/reviews", "/reviews/new?orderItemId=30", "/reviews/100/edit")) {
            mockMvc.perform(get(path).with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void adminReviewScreenRenders() throws Exception {
        when(reviewAdminService.getReviewPage(any(AdminReviewSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(adminView(null), adminView("답글입니다")),
                new PageRequest(1, 10), 2));

        mockMvc.perform(get("/admin/reviews").with(user(admin())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    /** 상품 상세의 후기 블록도 실데이터 경로로 렌더된다(컨트롤러 조합 지점). */
    @Test
    void productDetailRendersPublicReviews() throws Exception {
        when(productService.getProductDetail(8L)).thenReturn(productDetail());
        when(reviewService.getProductReviews(anyLong(), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(productReview()), new PageRequest(1, 5), 1));

        mockMvc.perform(get("/products/8"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    private ReviewableItemView item() {
        return new ReviewableItemView(30L, 55L, "ORD-20260726-ABC", 8L,
            "초코 가나슈 케이크", 1, NOW.minusDays(3));
    }

    private MyReviewView myReview(boolean hidden) {
        return new MyReviewView(100L, 8L, "초코 가나슈 케이크", 5, 5, null, 4,
            "맛있게 잘 먹었습니다.", List.of("/uploads/review/cake.jpg"),
            hidden ? "HIDDEN" : "VISIBLE", hidden ? "숨김" : "공개", hidden, NOW,
            hidden ? null : "감사합니다!");
    }

    private AdminReviewListView adminView(String reply) {
        return new AdminReviewListView(100L, "단골손님", 8L, "초코 가나슈 케이크", 5,
            "맛있게 잘 먹었습니다.", "VISIBLE", "공개", false, NOW, reply,
            reply == null ? null : NOW);
    }

    /** 후기 블록이 붙는 상품. 이 테스트의 다른 픽스처와 같은 8번 상품이다. */
    private ProductDetailView productDetail() {
        return new ProductDetailView(8L, "초코 가나슈 케이크", "설명", 38000L,
            "GENERAL", "일반 케이크", "ACTIVE", "판매 중", 10, "재고 있음",
            0, 0, null, new BigDecimal("4.50"), 1, true);
    }

    private ProductReviewView productReview() {
        return new ProductReviewView(100L, "단골손님", 5, 5, null, 4,
            "맛있게 잘 먹었습니다.", List.of("/uploads/review/cake.jpg"), NOW, "감사합니다!", NOW);
    }

    private MemberDetails customer() {
        return new MemberDetails(7L, "a@b.c", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MemberDetails admin() {
        return new MemberDetails(1L, "admin@cakeshop.local", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
