package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 관리자 상세 mapper 전용 프로젝션 — 제재 이력 컬럼까지 포함한다. */
@Getter
@Setter
public class AdminPostDetailRow {

    private Long id;
    private Long memberId;
    private String categoryName;
    private String title;
    private String content;
    private long viewCount;
    private long likeCount;
    private PostStatus status;
    private LocalDateTime blockedAt;
    private String blockedReason;
    private Long blockedBy;
    private LocalDateTime createdAt;

}
