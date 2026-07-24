package com.cakeshop.domain.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProductAdminController {

    @GetMapping("/admin/products")
    public String products() { return "admin/product/list"; }

    @GetMapping("/admin/products/new")
    public String createForm() { return "admin/product/form"; }

    @GetMapping("/admin/products/{productId}/edit")
    public String editForm(@PathVariable long productId) { return "admin/product/form"; }
}
