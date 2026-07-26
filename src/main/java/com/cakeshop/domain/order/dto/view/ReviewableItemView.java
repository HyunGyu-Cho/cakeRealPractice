package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * [공개 계약] 후기를 쓸 수 있는 주문 항목 하나 — 픽업까지 끝난 본인 주문의 항목이다.
 *
 * <p>review 도메인이 {@code orders}·{@code order_items}를 직접 조회하지 않도록 order가 내보내는 창구다.
 * 상품명은 주문 시점 스냅샷이라 상품이 바뀌거나 지워져도 그대로 남는다.
 */
public record ReviewableItemView(
    Long orderItemId,
    Long orderId,
    String orderNumber,
    Long productId,
    String productName,
    int quantity,
    LocalDateTime pickupAt) {
}
