package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.order.dto.form.CustomOrderForm;
import com.cakeshop.domain.order.service.CustomOrderService;
import com.cakeshop.domain.payment.dto.form.PaymentForm;
import com.cakeshop.domain.payment.service.CustomOrderPaymentService;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import java.util.Optional;
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

/** 주문제작 고객 화면. 목업 URL(/orders/custom/options·/request)과 템플릿을 유지한다. */
@Controller
@RequestMapping("/orders/custom")
public class CustomOrderController {

    private final CustomOrderService customOrderService;
    private final CustomOrderPaymentService customOrderPaymentService;

    public CustomOrderController(CustomOrderService customOrderService,
                                 CustomOrderPaymentService customOrderPaymentService) {
        this.customOrderService = customOrderService;
        this.customOrderPaymentService = customOrderPaymentService;
    }

    @GetMapping("/options")
    public String options(@RequestParam(name = "productId", required = false) Long productId,
                          Model model) {
        ProductDetailView product = resolveProduct(productId);
        if (product == null) {
            model.addAttribute("errorMessage", "주문제작 상품이 준비되지 않았습니다.");
            return "customer/order/custom-option";
        }
        model.addAttribute("product", product);
        model.addAttribute("optionGroups", customOrderService.getOptionGroups(product.id()));
        if (!model.containsAttribute("customOrderForm")) {
            CustomOrderForm form = new CustomOrderForm();
            form.setProductId(product.id());
            model.addAttribute("customOrderForm", form);
        }
        return "customer/order/custom-option";
    }

    @PostMapping("/options")
    public String submit(@Valid @ModelAttribute CustomOrderForm customOrderForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Long orderId = customOrderService.submitRequest(member.getMemberId(), customOrderForm);
                redirectAttributes.addFlashAttribute("successMessage",
                    "주문제작 요청이 접수되었습니다. 관리자 검토 후 견적을 보내드립니다.");
                return "redirect:/orders/custom/" + orderId;
            } catch (BusinessException exception) {
                // 화면에서 고칠 수 있는 오류는 리다이렉트 없이 폼에 되돌린다.
                bindingResult.reject(exception.getErrorCode().code(), exception.getMessage());
            }
        }
        ProductDetailView product = resolveProduct(customOrderForm.getProductId());
        if (product != null) {
            model.addAttribute("product", product);
            model.addAttribute("optionGroups", customOrderService.getOptionGroups(product.id()));
        }
        return "customer/order/custom-option";
    }

    /** orderId 없이 들어오는 목업 URL. 본인 최신 요청으로 보낸다. */
    @GetMapping("/request")
    public String latestRequest(@AuthenticationPrincipal MemberDetails member) {
        return customOrderService.findMyLatestRequestId(member.getMemberId())
            .map(orderId -> "redirect:/orders/custom/" + orderId)
            .orElse("redirect:/orders/custom/options");
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable Long orderId,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model) {
        model.addAttribute("request", customOrderService.getMyRequest(member.getMemberId(), orderId));
        model.addAttribute("cancelForm", new CancelForm());
        return "customer/order/custom-request";
    }

    @PostMapping("/{orderId:\\d+}/accept")
    public String accept(@PathVariable Long orderId,
                         @AuthenticationPrincipal MemberDetails member,
                         RedirectAttributes redirectAttributes) {
        String token = customOrderService.acceptQuote(member.getMemberId(), orderId);
        redirectAttributes.addFlashAttribute("successMessage",
            "견적을 수락했습니다. 결제를 진행해 주세요.");
        return "redirect:/orders/custom/pay/" + token;
    }

    @PostMapping("/{orderId:\\d+}/cancel")
    public String cancel(@PathVariable Long orderId,
                         @ModelAttribute CancelForm cancelForm,
                         @AuthenticationPrincipal MemberDetails member,
                         RedirectAttributes redirectAttributes) {
        customOrderService.cancelRequest(member.getMemberId(), orderId, cancelForm.getReason());
        redirectAttributes.addFlashAttribute("successMessage", "주문제작 요청을 취소했습니다.");
        return "redirect:/orders/custom/" + orderId;
    }

    // ---- 결제 링크 ----

    /**
     * 쿠폰 선택은 결제 폼과 분리해 GET으로 다시 그린다. 결제창을 열기 전에 서버가 금액을
     * 확정해 둬야 하므로, 선택이 바뀌면 준비 금액도 함께 다시 잡혀야 하기 때문이다.
     */
    @GetMapping("/pay/{token}")
    public String payment(@PathVariable String token,
                          @RequestParam(required = false) Long memberCouponId,
                          @AuthenticationPrincipal MemberDetails member,
                          Model model) {
        model.addAttribute("request",
            customOrderPaymentService.getPayableRequest(token, member.getMemberId()));
        model.addAttribute("token", token);
        model.addAttribute("coupons",
            customOrderPaymentService.getApplicableCoupons(token, member.getMemberId()));
        model.addAttribute("prepare",
            customOrderPaymentService.prepare(token, member.getMemberId(), memberCouponId));
        PaymentForm paymentForm = new PaymentForm();
        paymentForm.setMemberCouponId(memberCouponId);
        model.addAttribute("paymentForm", paymentForm);
        return "customer/order/custom-payment";
    }

    /** 모의 결제 경로. 결제창이 없어 폼 제출이 곧 승인 요청이다. */
    @PostMapping("/pay/{token}")
    public String pay(@PathVariable String token,
                      @ModelAttribute PaymentForm paymentForm,
                      @AuthenticationPrincipal MemberDetails member,
                      RedirectAttributes redirectAttributes) {
        Long orderId = customOrderPaymentService.pay(token, member.getMemberId(),
            paymentForm.getMethod(), paymentForm.getMemberCouponId());
        redirectAttributes.addFlashAttribute("successMessage",
            "결제가 완료되었습니다. 제작을 시작합니다.");
        return "redirect:/orders/" + orderId;
    }

    /** 실결제 성공 콜백. 쿠폰 선택은 successUrl에 실어 보낸 값을 그대로 받는다. */
    @GetMapping("/pay/{token}/success")
    public String confirm(@PathVariable String token,
                          @RequestParam String paymentKey,
                          @RequestParam("orderId") String tossOrderId,
                          @RequestParam long amount,
                          @RequestParam(defaultValue = "CARD") String method,
                          @RequestParam(required = false) Long memberCouponId,
                          @AuthenticationPrincipal MemberDetails member,
                          RedirectAttributes redirectAttributes) {
        Long orderId = customOrderPaymentService.confirm(token, member.getMemberId(), paymentKey,
            tossOrderId, amount, method, memberCouponId);
        redirectAttributes.addFlashAttribute("successMessage",
            "결제가 완료되었습니다. 제작을 시작합니다.");
        return "redirect:/orders/" + orderId;
    }

    /** 실결제 실패·이탈 콜백. 링크는 살아 있으므로 결제 화면으로 되돌린다. */
    @GetMapping("/pay/{token}/fail")
    public String fail(@PathVariable String token,
                       @RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       RedirectAttributes redirectAttributes) {
        customOrderPaymentService.markFailed(token, code, message);
        redirectAttributes.addFlashAttribute("errorMessage",
            message == null ? "결제가 완료되지 않았습니다." : message);
        return "redirect:/orders/custom/pay/" + token;
    }

    private ProductDetailView resolveProduct(Long productId) {
        if (productId != null) {
            return customOrderService.getCustomProduct(productId);
        }
        return customOrderService.findDefaultCustomProduct().orElse(null);
    }
}
