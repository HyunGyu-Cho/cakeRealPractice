package com.cakeshop.domain.coupon.controller;

import com.cakeshop.domain.coupon.dto.form.CouponForm;
import com.cakeshop.domain.coupon.dto.form.CouponIssueForm;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.service.CouponAdminService;
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

/** 관리자 쿠폰 관리 화면. product 관리자 CRUD 패턴을 그대로 따른다. */
@Controller
@RequestMapping("/admin/coupons")
public class CouponAdminController {

    private static final int PAGE_SIZE = 10;

    private final CouponAdminService couponAdminService;

    public CouponAdminController(CouponAdminService couponAdminService) {
        this.couponAdminService = couponAdminService;
    }

    @GetMapping
    public String list(@RequestParam(name = "page", defaultValue = "1") int page, Model model) {
        model.addAttribute("pageResult",
            couponAdminService.getCouponPage(new PageRequest(page, PAGE_SIZE)));
        model.addAttribute("extraQuery", "");
        return "admin/coupon/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("couponForm", new CouponForm());
        addReferenceData(model, null);
        return "admin/coupon/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("couponForm") CouponForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberDetails admin,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model, null);
            return "admin/coupon/form";
        }
        couponAdminService.createCoupon(form, admin.getMemberId());
        redirectAttributes.addFlashAttribute("successMessage", "쿠폰을 등록했습니다.");
        return "redirect:/admin/coupons";
    }

    @GetMapping("/{couponId}/edit")
    public String editForm(@PathVariable Long couponId, Model model) {
        model.addAttribute("couponForm", couponAdminService.getCouponForm(couponId));
        addReferenceData(model, couponId);
        return "admin/coupon/form";
    }

    @PostMapping("/{couponId}/edit")
    public String update(@PathVariable Long couponId,
                         @Valid @ModelAttribute("couponForm") CouponForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                couponAdminService.updateCoupon(couponId, form);
                redirectAttributes.addFlashAttribute("successMessage", "쿠폰을 저장했습니다.");
                return "redirect:/admin/coupons";
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addReferenceData(model, couponId);
        return "admin/coupon/form";
    }

    @PostMapping("/{couponId}/status")
    public String changeStatus(@PathVariable Long couponId,
                               @RequestParam CouponStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            couponAdminService.changeStatus(couponId, status);
            redirectAttributes.addFlashAttribute("successMessage",
                "쿠폰 상태를 " + status.label() + "(으)로 변경했습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/coupons";
    }

    @GetMapping("/{couponId}/issue")
    public String issueForm(@PathVariable Long couponId,
                            @RequestParam(name = "keyword", required = false) String keyword,
                            Model model) {
        addIssueModel(couponId, keyword, model);
        if (!model.containsAttribute("couponIssueForm")) {
            model.addAttribute("couponIssueForm", new CouponIssueForm());
        }
        return "admin/coupon/issue";
    }

    @PostMapping("/{couponId}/issue")
    public String issue(@PathVariable Long couponId,
                        @Valid @ModelAttribute CouponIssueForm couponIssueForm,
                        BindingResult bindingResult,
                        @RequestParam(name = "keyword", required = false) String keyword,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                couponAdminService.issueToMember(couponId, couponIssueForm.getMemberId());
                redirectAttributes.addFlashAttribute("successMessage", "쿠폰을 발급했습니다.");
                return "redirect:/admin/coupons";
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addIssueModel(couponId, keyword, model);
        return "admin/coupon/issue";
    }

    private void addIssueModel(Long couponId, String keyword, Model model) {
        model.addAttribute("coupon", couponAdminService.getCoupon(couponId));
        model.addAttribute("keyword", keyword);
        model.addAttribute("members", couponAdminService.searchIssueTargets(keyword));
    }

    private void addReferenceData(Model model, Long couponId) {
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("editingCouponId", couponId);
    }
}
