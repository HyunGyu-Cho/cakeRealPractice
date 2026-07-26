package com.cakeshop.domain.notification.event;

import com.cakeshop.domain.notification.dto.view.NotificationView;
import java.time.LocalDateTime;

/**
 * 커밋 후 실시간 전달용 이벤트.
 * 고객 알림은 수신자 한 명에게 보내므로 실제 알림 id를 담고,
 * 관리자 알림은 한 번의 브로드캐스트로 여러 수신자에게 가므로 id 없는 미리보기를 담는다.
 */
public record NotificationEvent(
    Long receiverId,
    String receiverUsername,
    boolean adminBroadcast,
    NotificationView notification,
    LocalDateTime occurredAt
) {
    public static NotificationEvent toMember(Long receiverId, String username,
                                             NotificationView notification) {
        return new NotificationEvent(receiverId, username, false, notification,
            LocalDateTime.now());
    }

    public static NotificationEvent toAdmins(NotificationView preview) {
        return new NotificationEvent(null, null, true, preview, LocalDateTime.now());
    }
}
