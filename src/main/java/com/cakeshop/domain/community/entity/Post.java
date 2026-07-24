package com.cakeshop.domain.community.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Post {

    private Long id;
    private Long memberId;
    private Long categoryId;
    private String title;
    private String content;
    private long viewCount;
    private long likeCount;
    private PostStatus status;
    private LocalDateTime blockedAt;
    private String blockedReason;
    private Long blockedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
