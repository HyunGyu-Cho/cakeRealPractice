package com.cakeshop.domain.notification.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /admin/notifications
@Controller
public class NotificationAdminController {

    @GetMapping("/admin/notifications")
    public String notifications() { return "admin/notification/list"; }
}
