package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.service.FulfillmentService;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FulfillmentAdminController {
    private FulfillmentService fulfillmentService;

    public FulfillmentAdminController() {
    }

    @Autowired
    public FulfillmentAdminController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/admin/fulfillment")
    public String fulfillment(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate,
        Model model) {
        LocalDate date = pickupDate == null ? LocalDate.now() : pickupDate;
        if (fulfillmentService != null) {
            model.addAttribute("orders", fulfillmentService.getOrders(date));
        }
        model.addAttribute("pickupDate", date);
        return "admin/fulfillment/list";
    }

    @PostMapping("/admin/fulfillment/{orderId}/status")
    public String changeStatus(@PathVariable Long orderId,
                               @RequestParam OrderStatus status,
                               @RequestParam
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDate,
                               RedirectAttributes redirectAttributes) {
        if (status == OrderStatus.READY_FOR_PICKUP) {
            fulfillmentService.markReady(orderId);
        } else if (status == OrderStatus.PICKED_UP) {
            fulfillmentService.markPickedUp(orderId);
        } else {
            throw new com.cakeshop.global.error.BusinessException(
                com.cakeshop.domain.order.error.OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        redirectAttributes.addFlashAttribute("successMessage", "픽업 상태를 변경했습니다.");
        return "redirect:/admin/fulfillment?pickupDate=" + pickupDate;
    }
}
