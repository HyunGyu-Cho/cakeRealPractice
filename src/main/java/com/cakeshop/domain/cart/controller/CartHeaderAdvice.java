package com.cakeshop.domain.cart.controller;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.global.common.web.RequestKind;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 고객 화면의 공통 헤더에 로그인 회원의 서버 장바구니 수량을 공급한다.
 *
 * <p>JSON 요청에서는 조회하지 않는다 — 모델을 아무도 읽지 않는데 COUNT 쿼리가 돌기 때문이다
 * (무한스크롤·좋아요·채팅 전송 등 fetch 경로 전부). 이 판정을 {@code @ControllerAdvice}의
 * selector 로 할 수 없는 이유는 {@link RequestKind} 주석에 있다.
 */
@ControllerAdvice
@Order(10)
public class CartHeaderAdvice {

    private final CartService cartService;

    public CartHeaderAdvice(CartService cartService) {
        this.cartService = cartService;
    }

    @ModelAttribute
    public void cartCount(@AuthenticationPrincipal MemberDetails member,
                          HttpServletRequest request,
                          Model model) {
        if (member != null && RequestKind.rendersView(request)) {
            model.addAttribute("cartCount", cartService.getTotalQuantity(member.getMemberId()));
        }
    }
}
