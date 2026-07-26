package com.cakeshop.domain.coupon.controller;

import com.cakeshop.domain.coupon.dto.view.MyCouponView;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 고객 쿠폰 화면 — 쿠폰함과 다운로드. */
@Controller
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/mypage/coupons")
    public String list(@AuthenticationPrincipal MemberDetails member, Model model) {
        List<MyCouponView> coupons = couponService.getMyCoupons(member.getMemberId());
        // 탭은 저장된 상태가 아니라 status + 만료 파생값으로 나눈다(스펙 2장).
        Map<String, List<MyCouponView>> grouped =
            coupons.stream().collect(Collectors.groupingBy(MyCouponView::state));
        model.addAttribute("availableCoupons", grouped.getOrDefault(MyCouponView.AVAILABLE, List.of()));
        model.addAttribute("usedCoupons", grouped.getOrDefault(MyCouponView.USED, List.of()));
        model.addAttribute("expiredCoupons", grouped.getOrDefault(MyCouponView.EXPIRED, List.of()));
        return "customer/coupon/list";
    }

    @GetMapping("/coupons")
    public String downloadable(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("coupons", couponService.getDownloadableCoupons(member.getMemberId()));
        return "customer/coupon/available";
    }

    @PostMapping("/coupons/{couponId}/download")
    public String download(@PathVariable Long couponId,
                           @AuthenticationPrincipal MemberDetails member,
                           RedirectAttributes redirectAttributes) {
        try {
            couponService.download(member.getMemberId(), couponId);
            redirectAttributes.addFlashAttribute("successMessage", "쿠폰을 받았습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/coupons";
    }
}
