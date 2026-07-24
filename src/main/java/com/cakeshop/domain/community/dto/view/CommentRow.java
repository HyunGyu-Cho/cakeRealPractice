package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.CommentStatus;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 댓글 목록 mapper 전용 프로젝션(전 상태). 노출 규칙은 service가 정하고, 닉네임은 member 도메인에서 채운다. */
@Getter
@Setter
public class CommentRow {

    private Long id;
    private Long memberId;
    private Long parentCommentId;
    private String content;
    private CommentStatus status;
    private LocalDateTime createdAt;

}
