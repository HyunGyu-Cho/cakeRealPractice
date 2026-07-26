package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

/**
 * 관리자 회원 상세. 활동 요약 3종은 order·review·community의 공개 계약으로 받는다.
 *
 * <p>{@code suspendable}·{@code releasable}은 화면이 조건을 다시 판단하지 않도록
 * 서비스가 상태 전이 규칙을 적용해 계산한 결과다.
 */
public record AdminMemberDetailView(
    Long id,
    String email,
    String nickname,
    String phone,
    String role,
    String status,
    String statusLabel,
    LocalDateTime createdAt,
    LocalDateTime suspendedAt,
    String suspendedReason,
    LocalDateTime withdrawnAt,
    long orderCount,
    long reviewCount,
    long postCount,
    boolean suspendable,
    boolean releasable
) {
}
