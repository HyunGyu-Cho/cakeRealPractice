package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.dto.form.PaymentForm;
import com.cakeshop.domain.payment.service.PaymentFacade;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentController {
    private PaymentFacade paymentFacade;
    private OrderService orderService;

    public PaymentController() {
    }

    @Autowired
    public PaymentController(PaymentFacade paymentFacade, OrderService orderService) {
        this.paymentFacade = paymentFacade;
        this.orderService = orderService;
    }

    @GetMapping("/orders/payment")
    public String payment(Principal principal,
                          HttpSession session, Model model) {
        MemberDetails member = member(principal);
        if (orderService == null || member == null) {
            return "customer/payment/form";
        }
        CheckoutDraft draft = draft(session);
        orderService.validateReadyForPayment(member.getMemberId(), draft);
        model.addAttribute("checkout", orderService.getCheckoutView(member.getMemberId(), draft));
        model.addAttribute("paymentForm", new PaymentForm());
        return "customer/payment/form";
    }

    @PostMapping("/orders/payment")
    public String pay(@RequestParam String checkoutId,
                      @Valid @ModelAttribute PaymentForm paymentForm,
                      BindingResult bindingResult,
                      Principal principal,
                      HttpSession session,
                      Model model) {
        MemberDetails member = member(principal);
        CheckoutDraft draft = draft(session);
        if (bindingResult.hasErrors()) {
            if (draft != null) {
                model.addAttribute("checkout",
                    orderService.getCheckoutView(member.getMemberId(), draft));
            }
            return "customer/payment/form";
        }
        Long orderId = paymentFacade.pay(
            member.getMemberId(), checkoutId, draft, paymentForm.getMethod());
        session.removeAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        return "redirect:/orders/complete?orderId=" + orderId;
    }

    /** 주문 생성 전 결제로 바뀐 뒤에도 기존 북마크·목업 테스트를 위한 호환 경로를 유지한다. */
    @GetMapping("/orders/{orderId:\\d+}/payment")
    public String legacyPayment(@PathVariable Long orderId) {
        return "customer/payment/form";
    }

    private CheckoutDraft draft(HttpSession session) {
        Object value = session.getAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        return value instanceof CheckoutDraft checkoutDraft ? checkoutDraft : null;
    }

    private MemberDetails member(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication
            && authentication.getPrincipal() instanceof MemberDetails details) {
            return details;
        }
        return null;
    }
}
