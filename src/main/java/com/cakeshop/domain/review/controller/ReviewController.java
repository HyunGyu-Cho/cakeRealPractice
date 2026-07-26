package com.cakeshop.domain.review.controller;

import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.service.ReviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 고객 후기 화면 — 내 후기함, 작성, 수정, 삭제. */
@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/reviews")
    public String list(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("reviewableItems", reviewService.getReviewableItems(member.getMemberId()));
        model.addAttribute("myReviews", reviewService.getMyReviews(member.getMemberId()));
        return "customer/review/list";
    }

    @GetMapping("/reviews/new")
    public String createForm(@RequestParam(name = "orderItemId", required = false) Long orderItemId,
                             @AuthenticationPrincipal MemberDetails member,
                             Model model) {
        // orderItemId 없이 들어온 목업 URL은 작성 가능 목록으로 보낸다.
        if (orderItemId == null) {
            return "redirect:/reviews";
        }
        model.addAttribute("target", reviewService.getReviewTarget(member.getMemberId(), orderItemId));
        if (!model.containsAttribute("reviewForm")) {
            ReviewForm form = new ReviewForm();
            form.setOrderItemId(orderItemId);
            model.addAttribute("reviewForm", form);
        }
        return "customer/review/form";
    }

    @PostMapping("/reviews")
    public String create(@RequestParam("orderItemId") Long orderItemId,
                         @Valid @ModelAttribute("reviewForm") ReviewForm reviewForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                reviewService.create(member.getMemberId(), orderItemId, reviewForm);
                redirectAttributes.addFlashAttribute("successMessage", "후기를 등록했습니다.");
                return "redirect:/reviews";
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        model.addAttribute("target", reviewService.getReviewTarget(member.getMemberId(), orderItemId));
        return "customer/review/form";
    }

    @GetMapping("/reviews/{reviewId}/edit")
    public String editForm(@PathVariable Long reviewId,
                           @AuthenticationPrincipal MemberDetails member,
                           Model model) {
        ReviewForm form = reviewService.getReviewForEdit(member.getMemberId(), reviewId);
        model.addAttribute("reviewForm", form);
        addEditModel(reviewId, form.getOrderItemId(), member, model);
        return "customer/review/form";
    }

    @PostMapping("/reviews/{reviewId}/edit")
    public String update(@PathVariable Long reviewId,
                         @Valid @ModelAttribute("reviewForm") ReviewForm reviewForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                reviewService.update(member.getMemberId(), reviewId, reviewForm);
                redirectAttributes.addFlashAttribute("successMessage", "후기를 수정했습니다.");
                return "redirect:/reviews";
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addEditModel(reviewId, reviewForm.getOrderItemId(), member, model);
        return "customer/review/form";
    }

    @PostMapping("/reviews/{reviewId}/delete")
    public String delete(@PathVariable Long reviewId,
                         @AuthenticationPrincipal MemberDetails member,
                         RedirectAttributes redirectAttributes) {
        try {
            reviewService.delete(member.getMemberId(), reviewId);
            redirectAttributes.addFlashAttribute("successMessage",
                "후기를 삭제했습니다. 같은 주문 상품에 다시 작성할 수 있습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/reviews";
    }

    private void addEditModel(Long reviewId, Long orderItemId, MemberDetails member, Model model) {
        model.addAttribute("editingReviewId", reviewId);
        model.addAttribute("currentImageUrls", reviewService.getImageUrls(reviewId));
        if (orderItemId != null) {
            model.addAttribute("target",
                reviewService.getReviewTarget(member.getMemberId(), orderItemId));
        }
    }
}
