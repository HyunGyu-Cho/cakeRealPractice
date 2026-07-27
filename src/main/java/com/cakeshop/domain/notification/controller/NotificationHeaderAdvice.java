package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.common.web.RequestKind;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 고객·관리자 공통 헤더에 로그인 회원의 미읽음 알림 개수를 공급한다.
 *
 * <p>JSON 요청에서 건너뛰는 이유는 {@code CartHeaderAdvice}와 같다 — 헛도는 COUNT 쿼리를 막는다.
 */
@ControllerAdvice
@Order(11)
public class NotificationHeaderAdvice {

    private final NotificationService notificationService;

    public NotificationHeaderAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void unreadNotificationCount(@AuthenticationPrincipal MemberDetails member,
                                        HttpServletRequest request,
                                        Model model) {
        if (member != null && RequestKind.rendersView(request)) {
            model.addAttribute("unreadNotificationCount",
                notificationService.countUnread(member.getMemberId()));
        }
    }
}
