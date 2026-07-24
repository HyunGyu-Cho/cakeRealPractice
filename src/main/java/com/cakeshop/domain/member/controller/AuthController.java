package com.cakeshop.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    // POST /login은 Spring Security가 처리하고 컨트롤러는 화면만 반환한다.
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "customer/member/signup";
    }
}
