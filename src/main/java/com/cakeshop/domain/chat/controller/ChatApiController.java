package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.form.ChatMessageForm;
import com.cakeshop.domain.chat.dto.form.ChatReadForm;
import com.cakeshop.domain.chat.dto.view.ChatImageView;
import com.cakeshop.domain.chat.dto.view.ChatMessageSliceView;
import com.cakeshop.domain.chat.dto.view.ChatMessageView;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
public class ChatApiController {

    private final ChatService chatService;

    public ChatApiController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/api/chat/messages")
    public ChatMessageSliceView messages(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal MemberDetails member) {
        return chatService.getCustomerMessages(
            member.getMemberId(), beforeId, afterId, size);
    }

    @PostMapping(path = "/api/chat/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageView> send(
            @Valid @ModelAttribute ChatMessageForm form,
            @AuthenticationPrincipal MemberDetails member) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(chatService.sendCustomerMessage(member.getMemberId(), form));
    }

    @PostMapping("/api/chat/read")
    public ResponseEntity<Void> read(
            @Valid @RequestBody ChatReadForm form,
            @AuthenticationPrincipal MemberDetails member) {
        chatService.readCustomerMessages(member.getMemberId(), form.lastMessageId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/chat/messages/{messageId}/image")
    public ResponseEntity<org.springframework.core.io.Resource> image(
            @PathVariable Long messageId,
            @AuthenticationPrincipal MemberDetails member) {
        boolean admin = member.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        ChatImageView image = chatService.getImage(messageId, member.getMemberId(), admin);
        ContentDisposition disposition = ContentDisposition.inline()
            .filename(image.originalName(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.parseMediaType(image.contentType()))
            .contentLength(image.size())
            .body(image.resource());
    }
}
