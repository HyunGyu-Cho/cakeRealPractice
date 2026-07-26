package com.cakeshop.domain.notification.event;

import com.cakeshop.domain.notification.dto.view.NotificationView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 커밋 후 실시간 전달용 이벤트. 고객·관리자 구분 없이 수신자 한 명을 향한다 —
 * 관리자 알림도 팬아웃한 각 행마다 이벤트를 발행하므로 항상 실제 알림 id를 담는다.
 * 수신자 username은 담지 않는다 — 조회가 업무 트랜잭션에 끼지 않도록 전송 시점에 해석한다.
 */
public record NotificationEvent(
    Long receiverId,
    /** 전달 이력 id. 클라이언트에는 내보내지 않는 내부 식별자다. */
    @JsonIgnore Long deliveryId,
    NotificationView notification,
    LocalDateTime occurredAt
) {
    public static NotificationEvent toMember(Long receiverId, Long deliveryId,
                                             NotificationView notification) {
        return new NotificationEvent(receiverId, deliveryId, notification, LocalDateTime.now());
    }
}
