package com.cakeshop.domain.community.dto.view;

import java.util.List;

/** 관리자 상세 읽기 모델. 제재 상태면 blocked* 필드가 채워지고, 신고 내역 전체를 함께 담는다. */
public record AdminPostDetailView(
    Long id,
    String categoryName,
    String title,
    String content,
    String nickname,
    long viewCount,
    long likeCount,
    String status,
    String statusLabel,
    String blockedDate,
    String blockedReason,
    String blockedByNickname,
    String createdDateTime,
    List<ReportView> reports,
    List<CommentView> comments
) {
}
