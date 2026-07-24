package com.cakeshop.domain.community.dto.view;

/** 관리자 목록 한 줄. status는 enum 이름, statusLabel은 화면 한글 라벨이다. */
public record AdminPostSummaryView(
    Long id,
    String categoryName,
    String title,
    String nickname,
    String createdDate,
    String status,
    String statusLabel
) {
}
