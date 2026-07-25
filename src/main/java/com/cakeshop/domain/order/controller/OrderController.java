package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.dto.form.PickupForm;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.service.RefundService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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

@Controller
@RequestMapping("/orders")
public class OrderController {
    private OrderService orderService;
    private RefundService refundService;

    public OrderController() {
        // 목업 라우트 호환용. Spring은 아래 생성자를 사용한다.
    }

    @Autowired
    public OrderController(OrderService orderService, RefundService refundService) {
        this.orderService = orderService;
        this.refundService = refundService;
    }

    @GetMapping("/pickup")
    public String pickupSetting(
        @RequestParam(name = "cartItemIds", required = false) List<Long> cartItemIds,
        @RequestParam(name = "pickupDate", required = false)
        @org.springframework.format.annotation.DateTimeFormat(
            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate pickupDate,
        Principal principal,
        HttpSession session,
        Model model) {
        MemberDetails member = member(principal);
        if (orderService == null || member == null) {
            return "customer/order/pickup-setting";
        }
        CheckoutDraft draft = draft(session);
        if (cartItemIds != null && !cartItemIds.isEmpty()
            && (draft == null || !draft.getMemberId().equals(member.getMemberId())
                || !draft.getCartItemIds().equals(cartItemIds))) {
            draft = orderService.startCheckout(member.getMemberId(), cartItemIds);
            session.setAttribute(CheckoutDraft.SESSION_ATTRIBUTE, draft);
        }
        if (draft == null) {
            return "redirect:/cart";
        }
        addPickupModel(member.getMemberId(), draft, pickupDate, model);
        if (!model.containsAttribute("pickupForm")) {
            PickupForm form = new PickupForm();
            form.setPickupAt(draft.getPickupAt());
            model.addAttribute("pickupForm", form);
        }
        return "customer/order/pickup-setting";
    }

    @PostMapping("/pickup")
    public String savePickup(
        @Valid @ModelAttribute PickupForm pickupForm,
        BindingResult bindingResult,
        Principal principal,
        HttpSession session,
        Model model) {
        MemberDetails member = member(principal);
        CheckoutDraft draft = requireDraft(member, session);
        if (!bindingResult.hasErrors()) {
            try {
                orderService.selectPickup(member.getMemberId(), draft, pickupForm.getPickupAt());
                return "redirect:/orders/checkout";
            } catch (BusinessException exception) {
                bindingResult.rejectValue("pickupAt", exception.getErrorCode().code(), exception.getMessage());
            }
        }
        addPickupModel(member.getMemberId(), draft,
            pickupForm.getPickupAt() == null ? null : pickupForm.getPickupAt().toLocalDate(), model);
        return "customer/order/pickup-setting";
    }

    @GetMapping("/checkout")
    public String checkout(Principal principal,
                           HttpSession session, Model model) {
        MemberDetails member = member(principal);
        if (orderService == null || member == null) {
            return "customer/order/form";
        }
        CheckoutDraft draft = requireDraft(member, session);
        if (draft.getPickupAt() == null) {
            return "redirect:/orders/pickup";
        }
        model.addAttribute("checkout", orderService.getCheckoutView(member.getMemberId(), draft));
        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", toForm(draft));
        }
        return "customer/order/form";
    }

    @PostMapping("/checkout")
    public String saveCheckout(
        @Valid @ModelAttribute("orderForm") GeneralOrderForm form,
        BindingResult bindingResult,
        Principal principal,
        HttpSession session,
        Model model) {
        MemberDetails member = member(principal);
        CheckoutDraft draft = requireDraft(member, session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("checkout", orderService.getCheckoutView(member.getMemberId(), draft));
            return "customer/order/form";
        }
        draft.updateOrderer(form);
        orderService.validateReadyForPayment(member.getMemberId(), draft);
        return "redirect:/orders/payment";
    }

    @GetMapping("/complete")
    public String complete(@RequestParam(name = "orderId", required = false) Long orderId,
                           Principal principal, Model model) {
        MemberDetails member = member(principal);
        if (orderService == null || member == null || orderId == null) {
            return "customer/order/complete";
        }
        model.addAttribute("order", orderService.getOwnedOrder(member.getMemberId(), orderId));
        return "customer/order/complete";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable Long orderId,
                         Principal principal, Model model) {
        MemberDetails member = member(principal);
        if (orderService == null || member == null) {
            return "customer/order/detail";
        }
        model.addAttribute("order", orderService.getOwnedOrder(member.getMemberId(), orderId));
        model.addAttribute("cancelForm", new CancelForm());
        return "customer/order/detail";
    }

    @PostMapping("/{orderId:\\d+}/cancel")
    public String cancel(@PathVariable Long orderId,
                         @Valid @ModelAttribute CancelForm cancelForm,
                         BindingResult bindingResult,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        MemberDetails member = member(principal);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/orders/" + orderId;
        }
        refundService.cancelByCustomer(member.getMemberId(), orderId, cancelForm.getReason());
        redirectAttributes.addFlashAttribute("successMessage", "주문이 취소되고 전액 환불되었습니다.");
        return "redirect:/orders/" + orderId;
    }

    @GetMapping("/custom/options")
    public String customOptions() {
        return "customer/order/custom-option";
    }

    @GetMapping("/custom/request")
    public String customRequest() {
        return "customer/order/custom-request";
    }

    private void addPickupModel(Long memberId, CheckoutDraft draft, LocalDate requestedDate, Model model) {
        CheckoutView checkout = orderService.getCheckoutView(memberId, draft);
        LocalDate selectedDate = requestedDate != null ? requestedDate
            : draft.getPickupAt() == null
                ? LocalDate.now().plusDays(checkout.maximumPreparationDays())
                : draft.getPickupAt().toLocalDate();
        model.addAttribute("checkout", checkout);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("slots", orderService.getPickupSlots(memberId, draft, selectedDate));
        model.addAttribute("minimumDate",
            LocalDate.now().plusDays(checkout.maximumPreparationDays()));
        model.addAttribute("maximumDate", LocalDate.now().plusDays(14));
    }

    private CheckoutDraft requireDraft(MemberDetails member, HttpSession session) {
        CheckoutDraft draft = draft(session);
        if (member == null || draft == null) {
            throw new BusinessException(com.cakeshop.domain.order.error.OrderErrorCode.CHECKOUT_NOT_FOUND);
        }
        // 소유권은 서비스에서도 다시 검증한다.
        orderService.getCheckoutView(member.getMemberId(), draft);
        return draft;
    }

    private CheckoutDraft draft(HttpSession session) {
        Object value = session.getAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        return value instanceof CheckoutDraft checkoutDraft ? checkoutDraft : null;
    }

    private GeneralOrderForm toForm(CheckoutDraft draft) {
        GeneralOrderForm form = new GeneralOrderForm();
        form.setOrdererName(draft.getOrdererName());
        form.setOrdererPhone(draft.getOrdererPhone());
        form.setPickupName(draft.getPickupName());
        form.setPickupPhone(draft.getPickupPhone());
        form.setRequestMessage(draft.getRequestMessage());
        return form;
    }

    private MemberDetails member(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication
            && authentication.getPrincipal() instanceof MemberDetails details) {
            return details;
        }
        return null;
    }
}
