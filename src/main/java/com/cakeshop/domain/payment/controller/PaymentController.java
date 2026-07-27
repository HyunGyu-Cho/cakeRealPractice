package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.dto.form.PaymentForm;
import com.cakeshop.domain.payment.service.PaymentFacade;
import com.cakeshop.domain.payment.service.PaymentService;
import com.cakeshop.global.error.BusinessException;
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
    private PaymentService paymentService;
    private OrderService orderService;

    public PaymentController() {
    }

    @Autowired
    public PaymentController(PaymentFacade paymentFacade, PaymentService paymentService,
                             OrderService orderService) {
        this.paymentFacade = paymentFacade;
        this.paymentService = paymentService;
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
        model.addAttribute("checkout", orderService.getCheckoutView(member.getMemberId(), draft));
        // 결제 준비: READY 결제 행을 만들고 화면이 결제를 시작할 값(주문번호·금액·키)을 받는다.
        model.addAttribute("prepare", paymentService.prepare(member.getMemberId(), draft));
        model.addAttribute("paymentForm", new PaymentForm());
        return "customer/payment/form";
    }

    /** 모의 결제 경로. 결제창이 없어 폼 제출이 곧 승인 요청이다. */
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
            return renderForm(member, draft, model);
        }
        Long orderId = paymentFacade.pay(
            member.getMemberId(), checkoutId, draft, paymentForm.getMethod());
        session.removeAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        return "redirect:/orders/complete?orderId=" + orderId;
    }

    /**
     * 실결제 성공 콜백. 제공자가 붙여 주는 {@code paymentKey}·{@code orderId}·{@code amount}에
     * 우리가 successUrl에 미리 넣어 둔 결제수단이 함께 온다.
     */
    @GetMapping("/orders/payment/success")
    public String confirm(@RequestParam String paymentKey,
                          @RequestParam("orderId") String tossOrderId,
                          @RequestParam long amount,
                          @RequestParam(defaultValue = "CARD") String method,
                          Principal principal,
                          HttpSession session) {
        MemberDetails member = member(principal);
        Long orderId = paymentFacade.confirm(
            member.getMemberId(), draft(session), paymentKey, tossOrderId, amount, method);
        session.removeAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        return "redirect:/orders/complete?orderId=" + orderId;
    }

    /**
     * 실결제 실패·이탈 콜백. 초안은 지우지 않는다 — 고객이 결제 화면에서 바로 다시 시도할 수 있어야 한다.
     * 준비된 결제 행만 마감하고, 새 시도는 새 READY 행을 만든다.
     */
    @GetMapping("/orders/payment/fail")
    public String fail(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       @RequestParam(value = "orderId", required = false) String tossOrderId,
                       Principal principal,
                       HttpSession session,
                       Model model) {
        if (tossOrderId != null) {
            paymentFacade.markFailed(tossOrderId, code, message);
        }
        model.addAttribute("errorMessage",
            message == null ? "결제가 완료되지 않았습니다." : message);
        return renderForm(member(principal), draft(session), model);
    }

    /** 주문 생성 전 결제로 바뀐 뒤에도 기존 북마크·목업 테스트를 위한 호환 경로를 유지한다. */
    @GetMapping("/orders/{orderId:\\d+}/payment")
    public String legacyPayment(@PathVariable Long orderId) {
        return "customer/payment/form";
    }

    /**
     * 검증 실패·결제 실패 시 리다이렉트 없이 결제 화면을 다시 그린다.
     * 준비가 더 이상 불가능한 상태(이미 확정된 체크아웃 등)면 요약만 보여준다.
     */
    private String renderForm(MemberDetails member, CheckoutDraft draft, Model model) {
        if (member != null && draft != null) {
            model.addAttribute("checkout", orderService.getCheckoutView(member.getMemberId(), draft));
            try {
                model.addAttribute("prepare", paymentService.prepare(member.getMemberId(), draft));
            } catch (BusinessException ignored) {
                // 결제를 다시 시작할 수 없는 상태다. 결제 버튼 없이 요약만 남긴다.
            }
        }
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
