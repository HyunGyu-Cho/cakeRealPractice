package com.cakeshop.domain.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PaymentController {

    @GetMapping("/orders/{orderId:\\d+}/payment")
    public String payment(@PathVariable("orderId") long orderId) {
        return "customer/payment/form";
    }
}
