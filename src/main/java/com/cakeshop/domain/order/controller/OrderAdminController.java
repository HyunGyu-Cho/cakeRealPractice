package com.cakeshop.domain.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OrderAdminController {

    @GetMapping("/admin/orders")
    public String orders() { return "admin/order/list"; }

    @GetMapping("/admin/orders/{orderId}")
    public String detail(@PathVariable long orderId) { return "admin/order/detail"; }
}
