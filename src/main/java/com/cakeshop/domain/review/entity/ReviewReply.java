package com.cakeshop.domain.review.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 관리자 답글. 후기당 1개다({@code uk_review_replies_review}). */
@Getter
@Setter
public class ReviewReply {
    private Long id;
    private Long reviewId;
    private Long adminId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
