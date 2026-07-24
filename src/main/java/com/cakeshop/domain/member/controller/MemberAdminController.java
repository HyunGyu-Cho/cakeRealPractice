package com.cakeshop.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/members
@Controller
public class MemberAdminController {

    @GetMapping("/admin/members")
    public String members() { return "admin/member/list"; }
}
