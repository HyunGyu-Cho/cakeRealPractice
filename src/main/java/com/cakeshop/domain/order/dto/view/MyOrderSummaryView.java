package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

public record MyOrderSummaryView(
    Long id,
    String orderNumber,
    String productSummary,
    Long finalAmount,
    String status,
    boolean custom,
    LocalDateTime pickupAt,
    LocalDateTime createdAt
) {
    /** 주문제작은 상세 화면이 따로다(견적·반려 이력을 보여준다). */
    public String detailUrl() {
        return custom ? "/orders/custom/" + id : "/orders/" + id;
    }

    public String statusLabel() {
        return switch (status) {
            case "UNDER_REVIEW" -> "확인 중";
            case "IN_PRODUCTION" -> "제작 중";
            case "PAID" -> "결제 완료";
            case "READY_FOR_PICKUP" -> "픽업 대기";
            case "PICKED_UP" -> "픽업 완료";
            case "CANCELED" -> "주문 취소";
            case "REJECTED" -> "주문 반려";
            default -> status;
        };
    }

    public String statusBadgeClass() {
        return switch (status) {
            case "UNDER_REVIEW" -> "badge--warning";
            case "IN_PRODUCTION", "READY_FOR_PICKUP" -> "badge--info";
            case "PAID", "PICKED_UP" -> "badge--success";
            case "CANCELED", "REJECTED" -> "badge--danger";
            default -> "";
        };
    }
}
