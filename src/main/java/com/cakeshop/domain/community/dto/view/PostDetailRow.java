package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 상세 조회 mapper 전용 프로젝션. status는 service의 노출 판단(BLOCKED/DELETED)에 쓴다. */
@Getter
@Setter
public class PostDetailRow {

    private Long id;
    private Long memberId;
    private String categoryCode;
    private String categoryName;
    private String title;
    private String content;
    private long viewCount;
    private long likeCount;
    private PostStatus status;
    private LocalDateTime createdAt;

}
