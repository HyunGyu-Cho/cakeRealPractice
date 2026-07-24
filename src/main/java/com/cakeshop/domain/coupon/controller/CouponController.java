package com.cakeshop.domain.coupon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CouponController {

    @GetMapping("/mypage/coupons")
    public String list() {
        return "customer/coupon/list";
    }
}
