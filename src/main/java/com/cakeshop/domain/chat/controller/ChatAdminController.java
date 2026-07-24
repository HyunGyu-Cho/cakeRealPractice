package com.cakeshop.domain.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/chat 목록·상세·답변
@Controller
public class ChatAdminController {

    @GetMapping("/admin/chat")
    public String list() {
        return "admin/chat/list";
    }
}
