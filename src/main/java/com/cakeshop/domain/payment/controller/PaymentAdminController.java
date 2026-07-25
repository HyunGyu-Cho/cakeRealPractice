package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.payment.service.PaymentService;
import com.cakeshop.domain.payment.service.RefundService;
import com.cakeshop.global.common.paging.PageRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentAdminController {
    private PaymentService paymentService;
    private RefundService refundService;

    public PaymentAdminController() {
    }

    @Autowired
    public PaymentAdminController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @GetMapping("/admin/payments")
    public String payments(@ModelAttribute("cond") PaymentSearchForm cond,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size,
                           Model model) {
        if (paymentService != null) {
            model.addAttribute("payments",
                paymentService.getPaymentPage(cond, new PageRequest(page, size)));
        }
        return "admin/payment/list";
    }

    @PostMapping("/admin/payments/orders/{orderId}/cancel")
    public String cancel(@PathVariable Long orderId,
                         @Valid @ModelAttribute CancelForm cancelForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/admin/payments";
        }
        refundService.cancelByAdmin(orderId, cancelForm.getReason());
        redirectAttributes.addFlashAttribute("successMessage", "주문을 취소하고 전액 환불했습니다.");
        return "redirect:/admin/payments";
    }
}
