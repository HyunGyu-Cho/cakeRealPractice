package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.dto.form.OrderSearchForm;
import com.cakeshop.domain.order.service.OrderAdminService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.global.common.paging.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OrderAdminController {
    private OrderAdminService orderAdminService;
    private OrderService orderService;

    public OrderAdminController() {
    }

    @Autowired
    public OrderAdminController(OrderAdminService orderAdminService, OrderService orderService) {
        this.orderAdminService = orderAdminService;
        this.orderService = orderService;
    }

    @GetMapping("/admin/orders")
    public String orders(@ModelAttribute("cond") OrderSearchForm cond,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size,
                         Model model) {
        if (orderAdminService != null) {
            model.addAttribute("orders",
                orderAdminService.getOrderPage(cond, new PageRequest(page, size)));
        }
        return "admin/order/list";
    }

    @GetMapping("/admin/orders/{orderId}")
    public String detail(@PathVariable Long orderId, Model model) {
        if (orderService != null) {
            model.addAttribute("order", orderService.getOrder(orderId));
        }
        return "admin/order/detail";
    }
}
