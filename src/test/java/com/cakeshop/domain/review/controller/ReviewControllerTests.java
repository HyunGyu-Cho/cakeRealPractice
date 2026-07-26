package com.cakeshop.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.service.ReviewService;
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

class ReviewControllerTests {

    private MockMvc mockMvc;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
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
    void listProvidesReviewableItemsAndMyReviews() throws Exception {
        when(reviewService.getReviewableItems(7L)).thenReturn(List.of(item()));
        when(reviewService.getMyReviews(7L)).thenReturn(List.of());

        mockMvc.perform(get("/reviews"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/list"))
            .andExpect(model().attributeExists("reviewableItems", "myReviews"));
    }

    @Test
    void createFormWithoutOrderItemRedirectsToTheList() throws Exception {
        // orderItemId 없이 들어온 목업 URL(/reviews/new)의 착지점
        mockMvc.perform(get("/reviews/new"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/reviews"));
    }

    @Test
    void createFormShowsTargetOrderItem() throws Exception {
        when(reviewService.getReviewTarget(7L, 30L)).thenReturn(item());

        mockMvc.perform(get("/reviews/new").param("orderItemId", "30"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeExists("target", "reviewForm"));
    }

    @Test
    void createRedirectsOnSuccess() throws Exception {
        when(reviewService.create(eq(7L), eq(30L), any(ReviewForm.class))).thenReturn(100L);

        mockMvc.perform(post("/reviews")
                .param("orderItemId", "30")
                .param("overallRating", "5")
                .param("content", "맛있게 잘 먹었습니다. 감사합니다."))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void shortContentReRendersTheFormWithoutRedirect() throws Exception {
        when(reviewService.getReviewTarget(7L, 30L)).thenReturn(item());

        mockMvc.perform(post("/reviews")
                .param("orderItemId", "30")
                .param("overallRating", "5")
                .param("content", "짧음"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeHasFieldErrors("reviewForm", "content"));

        verify(reviewService, never()).create(any(), any(), any());
    }

    @Test
    void missingOverallRatingIsRejected() throws Exception {
        when(reviewService.getReviewTarget(7L, 30L)).thenReturn(item());

        mockMvc.perform(post("/reviews")
                .param("orderItemId", "30")
                .param("content", "맛있게 잘 먹었습니다. 감사합니다."))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("reviewForm", "overallRating"));
    }

    @Test
    void businessFailureIsShownOnTheFormInsteadOfAnErrorPage() throws Exception {
        when(reviewService.create(eq(7L), eq(30L), any(ReviewForm.class)))
            .thenThrow(new BusinessException(ReviewErrorCode.ALREADY_WRITTEN));
        when(reviewService.getReviewTarget(7L, 30L)).thenReturn(item());

        mockMvc.perform(post("/reviews")
                .param("orderItemId", "30")
                .param("overallRating", "5")
                .param("content", "맛있게 잘 먹었습니다. 감사합니다."))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeHasErrors("reviewForm"));
    }

    @Test
    void deleteRedirectsWithMessage() throws Exception {
        mockMvc.perform(post("/reviews/100/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(reviewService).delete(7L, 100L);
    }

    @Test
    void deleteFailureIsShownAsAnErrorMessage() throws Exception {
        Mockito.doThrow(new BusinessException(ReviewErrorCode.NOT_OWNER))
            .when(reviewService).delete(7L, 100L);

        mockMvc.perform(post("/reviews/100/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    private ReviewableItemView item() {
        return new ReviewableItemView(30L, 55L, "ORD-20260726-ABC", 8L,
            "초코 가나슈 케이크", 1, LocalDateTime.of(2026, 7, 20, 14, 0));
    }
}
