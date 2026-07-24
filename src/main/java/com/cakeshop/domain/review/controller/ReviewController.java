package com.cakeshop.domain.review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReviewController {

    @GetMapping("/reviews/new")
    public String form() {
        return "customer/review/form";
    }
}
