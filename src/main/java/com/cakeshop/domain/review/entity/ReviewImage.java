package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

/** 후기 첨부 이미지. 저장은 {@code FileStorageClient}가 하고 여기에는 경로만 남는다. */
@Getter
@Setter
public class ReviewImage {
    private Long id;
    private Long reviewId;
    private String imageUrl;
    private Integer sortOrder;
}
