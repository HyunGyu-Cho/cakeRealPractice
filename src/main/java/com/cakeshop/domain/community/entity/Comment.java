package com.cakeshop.domain.community.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Comment {

    private Long id;
    private Long postId;
    private Long memberId;
    private Long parentCommentId;
    private String content;
    private CommentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
