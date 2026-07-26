package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.dto.form.QuoteForm;
import com.cakeshop.domain.order.dto.form.RejectForm;
import com.cakeshop.domain.order.service.CustomOrderAdminService;
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

/** 주문제작 관리자 화면. 목업이 없어 store·product 관리자 패턴을 따라 새로 만든다. */
@Controller
@RequestMapping("/admin/custom-orders")
public class CustomOrderAdminController {

    private final CustomOrderAdminService customOrderAdminService;

    public CustomOrderAdminController(CustomOrderAdminService customOrderAdminService) {
        this.customOrderAdminService = customOrderAdminService;
    }

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        model.addAttribute("pageResult",
            customOrderAdminService.getRequestPage(status, new PageRequest(page, 10)));
        model.addAttribute("currentStatus", status);
        model.addAttribute("extraQuery", status == null || status.isBlank()
            ? "" : "&status=" + status);
        return "admin/order/custom-list";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable Long orderId, Model model) {
        addDetailModel(orderId, model);
        return "admin/order/custom-detail";
    }

    @PostMapping("/{orderId:\\d+}/quote")
    public String quote(@PathVariable Long orderId,
                        @Valid @ModelAttribute QuoteForm quoteForm,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal MemberDetails admin,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                customOrderAdminService.quote(orderId, admin.getMemberId(), quoteForm);
                redirectAttributes.addFlashAttribute("successMessage", "견적을 발송했습니다.");
                return "redirect:/admin/custom-orders/" + orderId;
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addDetailModel(orderId, model);
        return "admin/order/custom-detail";
    }

    @PostMapping("/{orderId:\\d+}/reject")
    public String reject(@PathVariable Long orderId,
                         @Valid @ModelAttribute RejectForm rejectForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                customOrderAdminService.reject(orderId, rejectForm);
                redirectAttributes.addFlashAttribute("successMessage", "요청을 반려했습니다.");
                return "redirect:/admin/custom-orders/" + orderId;
            } catch (BusinessException exception) {
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addDetailModel(orderId, model);
        return "admin/order/custom-detail";
    }

    private void addDetailModel(Long orderId, Model model) {
        model.addAttribute("request", customOrderAdminService.getRequestDetail(orderId));
        if (!model.containsAttribute("quoteForm")) {
            model.addAttribute("quoteForm", new QuoteForm());
        }
        if (!model.containsAttribute("rejectForm")) {
            model.addAttribute("rejectForm", new RejectForm());
        }
    }
}
