package com.cakeshop.domain.coupon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/coupons
@Controller
public class CouponAdminController {

    @GetMapping("/admin/coupons")
    public String coupons() { return "admin/coupon/list"; }
}
