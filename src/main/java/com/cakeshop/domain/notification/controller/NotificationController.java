package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationController {

    private static final int PAGE_SIZE = 20;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("slice",
            notificationService.getSlice(member.getMemberId(), null, PAGE_SIZE));
        return "customer/notification/list";
    }
}
