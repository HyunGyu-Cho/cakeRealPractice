package com.cakeshop.domain.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/pickup")
    public String pickupSetting() {
        return "customer/order/pickup-setting";
    }

    @GetMapping("/custom/options")
    public String customOptions() {
        return "customer/order/custom-option";
    }

    @GetMapping("/custom/request")
    public String customRequest() {
        return "customer/order/custom-request";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "customer/order/form";
    }

    @GetMapping("/complete")
    public String complete() {
        return "customer/order/complete";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable("orderId") long orderId) {
        return "customer/order/detail";
    }
}
