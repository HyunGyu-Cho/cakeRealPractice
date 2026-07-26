package com.cakeshop.domain.notification.dto.view;

import java.time.LocalDateTime;

/** 관리자 발송 내역 한 줄. 수신자 정보는 MemberService 공개 계약으로 채운다(JOIN 금지). */
public record AdminNotificationView(
    Long id,
    Long receiverId,
    String receiverNickname,
    String type,
    String typeLabel,
    String title,
    String content,
    Long orderId,
    boolean read,
    LocalDateTime createdAt
) {
}
