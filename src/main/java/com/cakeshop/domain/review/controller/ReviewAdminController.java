package com.cakeshop.domain.review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/reviews 답글·숨김
@Controller
public class ReviewAdminController {

    @GetMapping("/admin/reviews")
    public String reviews() { return "admin/review/list"; }
}
