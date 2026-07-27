-- 알림 도메인: 후기 답글 알림 타입 추가
-- 적용 전제: V1 ~ V18 순서대로 적용된 스키마
-- 스펙: docs/specs/notification.md, docs/specs/review.md
--
-- 컬럼·테이블 변경은 없다. NotificationType 을 12개 → 13개로 늘리면서
-- chk_notifications_type 허용값에 REVIEW_REPLY 를 더한다.
-- (enum ↔ CHECK 동기화는 NotificationSqlSyncTests 가 고정한다)

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
            'REVIEW_REPLY',
            'ADMIN_CHAT_MESSAGE',
            'ADMIN_ORDER_PLACED',
            'ADMIN_ORDER_CANCELED'
        ));
