package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/**
 * {@code reviews} + 답글 조인 결과 한 행. 화면에 직접 노출하지 않는다 —
 * 서비스가 닉네임·이미지·라벨을 붙여 View로 바꾼다.
 *
 * <p>상품명은 review가 가진 값이 아니라 {@code order_items} 스냅샷이 아니라
 * {@code products} 조인 결과다. products는 남의 테이블이지만 후기 목록의 상품명 표시는
 * product 도메인의 공개 View로 대체할 수 없을 만큼 목록 조회에 붙어 있어,
 * 관리자 목록에서만 조인해 쓴다(고객 화면은 상품 상세 안이라 상품명이 이미 있다).
 */
public record ReviewRow(
    Long id,
    Long orderItemId,
    Long productId,
    Long memberId,
    int overallRating,
    Integer tasteRating,
    Integer designRating,
    Integer serviceRating,
    String content,
    String status,
    LocalDateTime createdAt,
    String replyContent,
    LocalDateTime replyCreatedAt) {
}
