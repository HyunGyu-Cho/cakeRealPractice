package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.dto.form.NotificationSearchForm;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationAdminService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.security.MemberDetails;
import java.util.Arrays;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** 관리자 알림 화면 — 본인 수신 알림과 전체 발송 내역(읽기 전용)을 함께 보여준다. */
@Controller
public class NotificationAdminController {

    private static final int MINE_SIZE = 20;

    private final NotificationService notificationService;
    private final NotificationAdminService notificationAdminService;

    public NotificationAdminController(NotificationService notificationService,
                                       NotificationAdminService notificationAdminService) {
        this.notificationService = notificationService;
        this.notificationAdminService = notificationAdminService;
    }

    @GetMapping("/admin/notifications")
    public String notifications(@ModelAttribute("cond") NotificationSearchForm cond,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size,
                                @AuthenticationPrincipal MemberDetails member,
                                Model model) {
        model.addAttribute("mine",
            notificationService.getSlice(member.getMemberId(), null, MINE_SIZE));
        model.addAttribute("notifications",
            notificationAdminService.getPage(cond, new PageRequest(page, size)));
        model.addAttribute("types", Arrays.asList(NotificationType.values()));
        return "admin/notification/list";
    }
}
