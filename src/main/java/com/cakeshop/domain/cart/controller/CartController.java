package com.cakeshop.domain.cart.controller;

import com.cakeshop.domain.cart.dto.form.CartAddForm;
import com.cakeshop.domain.cart.dto.form.CartUpdateForm;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/cart")
    public String cart(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("cart", cartService.getCart(member.getMemberId()));
        return "customer/cart/list";
    }

    @PostMapping("/cart/items")
    public String addItem(@Valid @ModelAttribute CartAddForm form,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal MemberDetails member,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return productRedirect(form.getProductId());
        }
        try {
            cartService.addItem(member.getMemberId(), form.getProductId(), form.getQuantity());
            redirectAttributes.addFlashAttribute("successMessage", "장바구니에 상품을 담았습니다.");
            return "redirect:/cart";
        } catch (BusinessException e) {
            if (e.getErrorCode() == CartErrorCode.ITEM_NOT_FOUND) {
                throw e;
            }
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return productRedirect(form.getProductId());
        }
    }

    @PostMapping("/cart/items/{cartItemId}/quantity")
    public String updateQuantity(@PathVariable Long cartItemId,
                                 @Valid @ModelAttribute CartUpdateForm form,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal MemberDetails member,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return "redirect:/cart";
        }
        try {
            cartService.updateQuantity(member.getMemberId(), cartItemId, form.getQuantity());
            redirectAttributes.addFlashAttribute("successMessage", "수량을 변경했습니다.");
        } catch (BusinessException e) {
            if (e.getErrorCode() == CartErrorCode.ITEM_NOT_FOUND) {
                throw e;
            }
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{cartItemId}/delete")
    public String deleteItem(@PathVariable Long cartItemId,
                             @AuthenticationPrincipal MemberDetails member,
                             RedirectAttributes redirectAttributes) {
        cartService.deleteItem(member.getMemberId(), cartItemId);
        redirectAttributes.addFlashAttribute("successMessage", "상품을 삭제했습니다.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/delete-selected")
    public String deleteSelected(
        @RequestParam(name = "itemIds", required = false) List<Long> itemIds,
        @AuthenticationPrincipal MemberDetails member,
        RedirectAttributes redirectAttributes) {
        try {
            cartService.deleteSelected(member.getMemberId(), itemIds);
            redirectAttributes.addFlashAttribute("successMessage", "선택한 상품을 삭제했습니다.");
        } catch (BusinessException e) {
            if (e.getErrorCode() == CartErrorCode.ITEM_NOT_FOUND) {
                throw e;
            }
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/delete-all")
    public String deleteAll(@AuthenticationPrincipal MemberDetails member,
                            RedirectAttributes redirectAttributes) {
        cartService.deleteAll(member.getMemberId());
        redirectAttributes.addFlashAttribute("successMessage", "장바구니를 비웠습니다.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/checkout")
    public String checkout(
        @RequestParam(name = "itemIds", required = false) List<Long> itemIds,
        @AuthenticationPrincipal MemberDetails member,
        RedirectAttributes redirectAttributes) {
        try {
            var checkoutItems = cartService.getCheckoutItems(member.getMemberId(), itemIds);
            UriComponentsBuilder target = UriComponentsBuilder.fromPath("/orders/pickup");
            checkoutItems.forEach(item -> target.queryParam("cartItemIds", item.cartItemId()));
            return "redirect:" + target.build().toUriString();
        } catch (BusinessException e) {
            if (e.getErrorCode() == CartErrorCode.ITEM_NOT_FOUND) {
                throw e;
            }
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cart";
        }
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getAllErrors().getFirst().getDefaultMessage();
    }

    private String productRedirect(Long productId) {
        return productId != null && productId > 0
            ? "redirect:/products/" + productId
            : "redirect:/products";
    }
}
