package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/**
 * [공개 계약] 홈 메인에 노출하는 후기 한 건. {@code VISIBLE}만 나간다.
 *
 * <p>작성자는 닉네임만 담고(회원 식별 정보 없음), 이미지는 첫 장만 썸네일로 쓴다.
 */
public record BestReviewView(
    Long reviewId,
    Long productId,
    String productName,
    String writerNickname,
    int overallRating,
    String content,
    String thumbnailUrl,
    LocalDateTime createdAt) {
}
