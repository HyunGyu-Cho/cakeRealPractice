package com.cakeshop.domain.order.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/fulfillment
@Controller
public class FulfillmentAdminController {

    @GetMapping("/admin/fulfillment")
    public String fulfillment() { return "admin/fulfillment/list"; }
}
