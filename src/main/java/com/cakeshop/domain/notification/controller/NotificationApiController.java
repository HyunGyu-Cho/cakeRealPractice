package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.dto.view.NotificationSliceView;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고객·관리자 공통 알림 API. 수신자는 항상 로그인 회원 본인이다. */
@RestController
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications")
    public NotificationSliceView slice(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal MemberDetails member) {
        return notificationService.getSlice(member.getMemberId(), cursor, size);
    }

    @PostMapping("/api/notifications/{notificationId}/read")
    public ResponseEntity<Void> read(@PathVariable Long notificationId,
                                     @AuthenticationPrincipal MemberDetails member) {
        notificationService.markRead(notificationId, member.getMemberId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal MemberDetails member) {
        notificationService.markAllRead(member.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
