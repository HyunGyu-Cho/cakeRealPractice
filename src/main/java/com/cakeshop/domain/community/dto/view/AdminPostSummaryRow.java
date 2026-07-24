package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 관리자 목록 mapper 전용 프로젝션 — 상태 무관 전체 글이 대상이다. */
@Getter
@Setter
public class AdminPostSummaryRow {

    private Long id;
    private Long memberId;
    private String categoryName;
    private String title;
    private PostStatus status;
    private LocalDateTime createdAt;

}
