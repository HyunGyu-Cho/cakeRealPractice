package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

/**
 * 관리자 회원 목록 한 행. 주문 건수는 orders를 JOIN하지 않고
 * order 도메인 공개 계약(getOrderCountMap)으로 채운다.
 */
public record AdminMemberListView(
    Long id,
    String nickname,
    String email,
    String phone,
    LocalDateTime createdAt,
    long orderCount,
    String status,
    String statusLabel,
    boolean admin
) {
}
