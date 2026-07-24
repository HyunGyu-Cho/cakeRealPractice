package com.cakeshop.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyPageController {

    @GetMapping("/mypage")
    public String myPage() {
        return "customer/member/mypage";
    }

    @GetMapping("/mypage/profile")
    public String profile() {
        return "customer/member/profile-edit";
    }
}
