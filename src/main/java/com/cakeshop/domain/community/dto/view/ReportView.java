package com.cakeshop.domain.community.dto.view;

/** 관리자 상세의 신고 내역 한 줄. */
public record ReportView(
    Long id,
    String reporterNickname,
    String reason,
    String statusLabel,
    String createdDate
) {
}
