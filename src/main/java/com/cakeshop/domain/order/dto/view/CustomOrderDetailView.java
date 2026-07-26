package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문제작 요청 상세. 화면 라벨({@code progressLabel})은 주문 상태와 최신 견적 상태를 조합한
 * <b>파생값</b>이며 저장하지 않는다.
 */
public record CustomOrderDetailView(
    Long orderId,
    String orderNumber,
    Long memberId,
    String productName,
    String imageUrl,
    List<CustomOrderOptionView> options,
    String lettering,
    String requirements,
    List<String> referenceImageUrls,
    LocalDateTime pickupAt,
    Long desiredBudget,
    long estimatedAmount,
    Long finalAmount,
    String status,
    String statusLabel,
    String progressLabel,
    String rejectReason,
    List<CustomOrderQuoteView> quotes,
    CustomOrderQuoteView latestQuote,
    String paymentToken,
    LocalDateTime paymentExpiresAt,
    boolean acceptable,
    boolean payable,
    boolean cancellable,
    LocalDateTime createdAt
) {
}
