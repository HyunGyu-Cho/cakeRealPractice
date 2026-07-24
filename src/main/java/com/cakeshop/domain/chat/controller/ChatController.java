package com.cakeshop.domain.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /chat 최초 진입 (SSR)
@Controller
public class ChatController {

    @GetMapping("/chat")
    public String room() {
        return "customer/chat/room";
    }
}
