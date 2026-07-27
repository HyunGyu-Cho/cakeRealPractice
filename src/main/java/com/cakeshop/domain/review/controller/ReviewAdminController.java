package com.cakeshop.domain.review.controller;

import com.cakeshop.domain.review.dto.form.AdminReviewSearchForm;
import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.service.ReviewAdminService;
import com.cakeshop.global.common.paging.PageQuery;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 관리자 후기 관리 — 목록·검색, 숨김·복구, 답글. */
@Controller
@RequestMapping("/admin/reviews")
public class ReviewAdminController {

    private static final int PAGE_SIZE = 10;

    private final ReviewAdminService reviewAdminService;

    public ReviewAdminController(ReviewAdminService reviewAdminService) {
        this.reviewAdminService = reviewAdminService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") AdminReviewSearchForm search, Model model) {
        model.addAttribute("pageResult",
            reviewAdminService.getReviewPage(search, new PageRequest(search.getPage(), PAGE_SIZE)));
        model.addAttribute("reviewStatuses", ReviewStatus.values());
        model.addAttribute("replyForm", new ReviewReplyForm());
        model.addAttribute("extraQuery", extraQuery(search));
        return "admin/review/list";
    }

    @PostMapping("/{reviewId}/status")
    public String changeStatus(@PathVariable Long reviewId,
                               @RequestParam ReviewStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            reviewAdminService.changeStatus(reviewId, status);
            redirectAttributes.addFlashAttribute("successMessage",
                status == ReviewStatus.HIDDEN ? "후기를 숨겼습니다." : "후기를 다시 공개했습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/{reviewId}/reply")
    public String saveReply(@PathVariable Long reviewId,
                            @Valid @ModelAttribute("replyForm") ReviewReplyForm replyForm,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal MemberDetails admin,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/admin/reviews";
        }
        try {
            reviewAdminService.saveReply(reviewId, admin.getMemberId(), replyForm);
            redirectAttributes.addFlashAttribute("successMessage", "답글을 저장했습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    private String extraQuery(AdminReviewSearchForm search) {
        return PageQuery.of()
            .add("keyword", search.getNormalizedKeyword())
            .add("status", search.getNormalizedStatus())
            .add("rating", search.getNormalizedRating())
            .toQueryString();
    }
}
