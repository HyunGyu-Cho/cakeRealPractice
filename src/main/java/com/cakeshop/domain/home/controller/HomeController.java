package com.cakeshop.domain.home.controller;

import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    /** 비로그인도 열리는 화면이라 principal은 null일 수 있다(쿠폰 섹션만 로그인 회원에게 보인다). */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberDetails member, Model model) {
        Long memberId = member == null ? null : member.getMemberId();
        model.addAttribute("store", homeService.getStore());
        model.addAttribute("categories", homeService.getCategories());
        model.addAttribute("popularProducts", homeService.getPopularProducts());
        model.addAttribute("downloadableCoupons", homeService.getDownloadableCoupons(memberId));
        model.addAttribute("bestReviews", homeService.getBestReviews());
        model.addAttribute("popularPosts", homeService.getPopularPosts());
        return "home/main";
    }

    @GetMapping("/screens")
    public String screens() {
        return "home/screens";
    }
}
