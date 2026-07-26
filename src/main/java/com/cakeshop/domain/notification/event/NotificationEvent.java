package com.cakeshop.domain.notification.event;

import com.cakeshop.domain.notification.dto.view.NotificationView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 커밋 후 실시간 전달용 이벤트.
 * 고객 알림은 수신자 한 명에게 보내므로 실제 알림 id와 전달 이력 id를 담고,
 * 관리자 알림은 한 번의 브로드캐스트로 여러 수신자에게 가므로 id 없는 미리보기를 담는다.
 * 수신자 username은 담지 않는다 — 조회가 업무 트랜잭션에 끼지 않도록 전송 시점에 해석한다.
 */
public record NotificationEvent(
    Long receiverId,
    /** 전달 이력 id. 관리자 브로드캐스트는 null이며 클라이언트에는 내보내지 않는다. */
    @JsonIgnore Long deliveryId,
    boolean adminBroadcast,
    NotificationView notification,
    LocalDateTime occurredAt
) {
    public static NotificationEvent toMember(Long receiverId, Long deliveryId,
                                             NotificationView notification) {
        return new NotificationEvent(receiverId, deliveryId, false, notification,
            LocalDateTime.now());
    }

    public static NotificationEvent toAdmins(NotificationView preview) {
        return new NotificationEvent(null, null, true, preview, LocalDateTime.now());
    }
}
