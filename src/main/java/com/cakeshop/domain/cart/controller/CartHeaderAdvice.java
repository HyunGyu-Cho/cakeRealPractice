package com.cakeshop.domain.cart.controller;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 고객 화면의 공통 헤더에 로그인 회원의 서버 장바구니 수량을 공급한다. */
@ControllerAdvice
@Order(10)
public class CartHeaderAdvice {

    private final CartService cartService;

    public CartHeaderAdvice(CartService cartService) {
        this.cartService = cartService;
    }

    @ModelAttribute
    public void cartCount(@AuthenticationPrincipal MemberDetails member, Model model) {
        if (member != null) {
            model.addAttribute("cartCount", cartService.getTotalQuantity(member.getMemberId()));
        }
    }
}
