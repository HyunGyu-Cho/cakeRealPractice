package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/** 관리자 후기 목록 한 줄. 답글은 있으면 함께 보여주고 목록에서 바로 수정한다. */
public record AdminReviewListView(
    Long id,
    String writerNickname,
    Long productId,
    String productName,
    int overallRating,
    String content,
    String status,
    String statusLabel,
    boolean hidden,
    LocalDateTime createdAt,
    String replyContent,
    LocalDateTime replyCreatedAt) {
}
