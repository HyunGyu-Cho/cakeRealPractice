package com.cakeshop.domain.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/products")
public class ProductController {

    /** 화면 선이관 단계: ProductService가 연결되면 동일한 URL에 Model 데이터만 추가한다. */
    @GetMapping
    public String list() {
        return "customer/product/list";
    }

    @GetMapping("/{productId:\\d+}")
    public String detail(@PathVariable("productId") long productId) {
        return "customer/product/detail";
    }
}
