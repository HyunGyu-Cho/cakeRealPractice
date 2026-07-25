package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.view.AdminChatPageView;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatAdminController {

    private final ChatService chatService;

    public ChatAdminController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/admin/chat")
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long roomId,
            @AuthenticationPrincipal MemberDetails admin,
            Model model) {
        AdminChatPageView chatPage = chatService.getAdminRooms(keyword, filter, page, 20);
        Long selectedId = roomId;
        if (selectedId == null && !chatPage.rooms().isEmpty()) {
            selectedId = chatPage.rooms().getFirst().roomId();
        }
        model.addAttribute("chatPage", chatPage);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("filter", filter);
        model.addAttribute("selectedChat",
            selectedId == null ? null : chatService.getAdminRoom(selectedId, admin.getMemberId()));
        return "admin/chat/list";
    }
}
