package com.cakeshop.domain.review.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 후기 한 건. 작성 단위는 회원·상품이 아니라 <b>주문 항목</b>이다
 * ({@code uk_reviews_order_item}) — 같은 상품을 여러 번 사면 각각 쓸 수 있다.
 *
 * <p>세부 3축(맛·디자인·응대)은 선택이라 {@code null}일 수 있다. 상품 평점 집계는
 * {@code overallRating}만 쓴다.
 */
@Getter
@Setter
public class Review {
    private Long id;
    private Long orderItemId;
    private Long productId;
    private Long memberId;
    private Integer overallRating;
    private Integer tasteRating;
    private Integer designRating;
    private Integer serviceRating;
    private String content;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
