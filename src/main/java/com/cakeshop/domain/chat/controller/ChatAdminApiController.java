package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.form.ChatMessageForm;
import com.cakeshop.domain.chat.dto.form.ChatReadForm;
import com.cakeshop.domain.chat.dto.view.ChatMessageSliceView;
import com.cakeshop.domain.chat.dto.view.ChatMessageView;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatAdminApiController {

    private final ChatService chatService;

    public ChatAdminApiController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/admin/api/chat/rooms/{roomId}/messages")
    public ChatMessageSliceView messages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal MemberDetails admin) {
        return chatService.getAdminMessages(
            roomId, admin.getMemberId(), beforeId, afterId, size);
    }

    @PostMapping(
        path = "/admin/api/chat/rooms/{roomId}/messages",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ChatMessageView> send(
            @PathVariable Long roomId,
            @Valid @ModelAttribute ChatMessageForm form,
            @AuthenticationPrincipal MemberDetails admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(chatService.sendAdminMessage(roomId, admin.getMemberId(), form));
    }

    @PostMapping("/admin/api/chat/rooms/{roomId}/read")
    public ResponseEntity<Void> read(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatReadForm form,
            @AuthenticationPrincipal MemberDetails admin) {
        chatService.readAdminMessages(roomId, admin.getMemberId(), form.lastMessageId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/api/chat/rooms/{roomId}/close")
    public ResponseEntity<Void> close(@PathVariable Long roomId) {
        chatService.closeRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
