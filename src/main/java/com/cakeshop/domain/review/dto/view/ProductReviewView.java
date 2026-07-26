package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [공개 계약] 상품 상세에 노출하는 후기 한 건. {@code VISIBLE}만 나간다.
 *
 * <p>작성자는 닉네임만 보여준다(회원 식별 정보는 담지 않는다).
 */
public record ProductReviewView(
    Long id,
    String writerNickname,
    int overallRating,
    Integer tasteRating,
    Integer designRating,
    Integer serviceRating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt,
    String replyContent,
    LocalDateTime replyCreatedAt) {

    public boolean hasReply() {
        return replyContent != null;
    }
}
