package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 후기함의 한 건. 숨김 처리된 후기도 <b>본인에게는 보인다</b> —
 * 흔적 없이 사라지면 문의가 늘기 때문이다(스펙 6장 규칙 6).
 */
public record MyReviewView(
    Long id,
    Long productId,
    String productName,
    int overallRating,
    Integer tasteRating,
    Integer designRating,
    Integer serviceRating,
    String content,
    List<String> imageUrls,
    String status,
    String statusLabel,
    boolean hidden,
    LocalDateTime createdAt,
    String replyContent) {
}
