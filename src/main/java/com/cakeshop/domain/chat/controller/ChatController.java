package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String room(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("chat", chatService.getCustomerRoom(member.getMemberId()));
        return "customer/chat/room";
    }
}
