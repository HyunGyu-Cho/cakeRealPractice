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

import com.cakeshop.domain.review.dto.form.AdminReviewSearchForm;
import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.service.ReviewAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewAdminControllerTests {

    private MockMvc mockMvc;
    private ReviewAdminService reviewAdminService;

    @BeforeEach
    void setUp() {
        reviewAdminService = mock(ReviewAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewAdminController(reviewAdminService))
            .setCustomArgumentResolvers(
                new org.springframework.security.web.method.annotation
                    .AuthenticationPrincipalArgumentResolver())
            .build();

        MemberDetails admin = new MemberDetails(1L, "admin@cakeshop.local", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(admin, "pw", admin.getAuthorities()));
    }

    @Test
    void listKeepsSearchConditionsInPageLinks() throws Exception {
        when(reviewAdminService.getReviewPage(any(AdminReviewSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 10), 0));

        mockMvc.perform(get("/admin/reviews").param("status", "HIDDEN").param("rating", "5"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/review/list"))
            .andExpect(model().attributeExists("pageResult", "reviewStatuses", "replyForm"))
            .andExpect(model().attribute("extraQuery", "&status=HIDDEN&rating=5"));
    }

    @Test
    void unknownStatusIsDroppedInsteadOfReachingTheQuery() throws Exception {
        when(reviewAdminService.getReviewPage(any(AdminReviewSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 10), 0));

        mockMvc.perform(get("/admin/reviews").param("status", "DELETED"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("extraQuery", ""));
    }

    @Test
    void hideRedirectsWithMessage() throws Exception {
        mockMvc.perform(post("/admin/reviews/100/status").param("status", "HIDDEN"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/reviews"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(reviewAdminService).changeStatus(100L, ReviewStatus.HIDDEN);
    }

    @Test
    void replySavesWithTheLoggedInAdmin() throws Exception {
        mockMvc.perform(post("/admin/reviews/100/reply").param("content", "감사합니다!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/reviews"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(reviewAdminService).saveReply(eq(100L), eq(1L), any(ReviewReplyForm.class));
    }

    @Test
    void emptyReplyIsRejected() throws Exception {
        mockMvc.perform(post("/admin/reviews/100/reply").param("content", " "))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        verify(reviewAdminService, never()).saveReply(any(), any(), any());
    }
}
