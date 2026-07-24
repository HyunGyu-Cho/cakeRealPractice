package com.cakeshop.domain.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/payments 결제·환불 관리
@Controller
public class PaymentAdminController {

    @GetMapping("/admin/payments")
    public String payments() { return "admin/payment/list"; }
}
