-- 알림 도메인: notification_type 값 제약과 조회 인덱스
-- 적용 전제: V1 ~ V9 순서대로 적용된 스키마
-- 스펙: docs/specs/notification.md
--
-- 컬럼 변경은 없다. 값 집합(NotificationType 12개) 고정과
-- 목록 키셋 페이징 / 헤더 미읽음 카운트용 인덱스만 추가한다.

SET NAMES utf8mb4;

ALTER TABLE `notifications`
    DROP CONSTRAINT IF EXISTS `chk_notifications_type`,
    ADD CONSTRAINT `chk_notifications_type`
        CHECK (`notification_type` IN (
            'CHAT_MESSAGE',
            'ORDER_PAID',
            'ORDER_IN_PRODUCTION',
            'ORDER_READY_FOR_PICKUP',
            'ORDER_PICKED_UP',
            'ORDER_CANCELED',
            'ORDER_REJECTED',
            'CUSTOM_ORDER_QUOTE',
            'PAYMENT_REQUESTED',
            'ADMIN_CHAT_MESSAGE',
            'ADMIN_ORDER_PLACED',
            'ADMIN_ORDER_CANCELED'
        ));

-- 수신자별 최신순 목록(키셋 페이징: receiver_id = ? AND id < ? ORDER BY id DESC)
ALTER TABLE `notifications`
    ADD INDEX IF NOT EXISTS `idx_notifications_receiver` (`receiver_id`, `id` DESC);

-- 헤더 미읽음 개수 집계
ALTER TABLE `notifications`
    ADD INDEX IF NOT EXISTS `idx_notifications_unread` (`receiver_id`, `is_read`);
