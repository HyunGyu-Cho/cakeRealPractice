package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/**
 * 관리자 목록·내 후기함이 쓰는 행. 상품명이 필요해 {@code products}를 조인한다 —
 * 후기 목록에서 상품명 없이는 화면이 성립하지 않고, 상품 하나하나를 공개 View로 재조회하면 N+1이 된다.
 * 대신 {@code members}는 조인하지 않고 닉네임은 {@code MemberService.getNicknameMap}으로 채운다.
 */
public record AdminReviewRow(
    Long id,
    Long memberId,
    Long productId,
    String productName,
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
