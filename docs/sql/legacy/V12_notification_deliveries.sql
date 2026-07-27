-- 알림 전달 이력: 커밋 후 즉시 STOMP 푸시 + 실패분 스케줄러 재시도(최대 3회)
-- 적용 전제: V1 ~ V11 순서대로 적용된 스키마
-- 스펙: docs/specs/notification.md
--
-- V0_ERD.sql에 설계만 있고 생성된 적 없던 테이블을 실제로 만든다.
-- ERD 원안 대비 변경점:
--   - status/channel: VARCHAR(30) -> VARCHAR(20) + CHECK (conventions.md 상태 컬럼 규칙)
--   - recipient: NOT NULL -> NULL (업무 트랜잭션에서 회원을 조회하지 않기 위해 전송 시점에 채운다)
--   - retry_count / next_retry_at / idx_notification_deliveries_retry 추가 (재시도 백오프)

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `notification_deliveries` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `notification_id`     BIGINT NOT NULL,
    `channel`             VARCHAR(20) NOT NULL,
    -- 전송 시점에 브로드캐스터/스케줄러가 해석해 채운다(업무 트랜잭션에서 회원 조회 금지)
    `recipient`           VARCHAR(500) NULL,
    `template_code`       VARCHAR(100) NULL,   -- WEBSOCKET 채널 미사용(외부 채널 확장용)
    `provider_message_id` VARCHAR(200) NULL,   -- WEBSOCKET 채널 미사용(외부 채널 확장용)
    `status`              VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    `retry_count`         INT NOT NULL DEFAULT 0,
    `next_retry_at`       DATETIME(6) NULL,
    `failure_code`        VARCHAR(100) NULL,
    `failure_reason`      VARCHAR(500) NULL,
    `requested_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `sent_at`             DATETIME(6) NULL,
    `delivered_at`        DATETIME(6) NULL,    -- WEBSOCKET 채널 미사용(수신 확인 없음)
    `clicked_at`          DATETIME(6) NULL,    -- WEBSOCKET 채널 미사용
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_notification_deliveries_retry` (`status`, `next_retry_at`),
    CONSTRAINT `chk_notification_deliveries_status`
        CHECK (`status` IN ('REQUESTED', 'SENT', 'FAILED', 'ABANDONED')),
    CONSTRAINT `chk_notification_deliveries_channel`
        CHECK (`channel` IN ('WEBSOCKET')),
    CONSTRAINT `fk_notification_deliveries_notification`
        FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V0_ERD.sql 골격을 그대로 만들어 둔 DB를 위한 제자리 업그레이드
ALTER TABLE `notification_deliveries`
    ADD COLUMN IF NOT EXISTS `retry_count` INT NOT NULL DEFAULT 0 AFTER `status`,
    ADD COLUMN IF NOT EXISTS `next_retry_at` DATETIME(6) NULL AFTER `retry_count`,
    MODIFY COLUMN `channel` VARCHAR(20) NOT NULL,
    MODIFY COLUMN `recipient` VARCHAR(500) NULL,
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'REQUESTED';

ALTER TABLE `notification_deliveries`
    DROP CONSTRAINT IF EXISTS `chk_notification_deliveries_status`,
    ADD CONSTRAINT `chk_notification_deliveries_status`
        CHECK (`status` IN ('REQUESTED', 'SENT', 'FAILED', 'ABANDONED'));

ALTER TABLE `notification_deliveries`
    DROP CONSTRAINT IF EXISTS `chk_notification_deliveries_channel`,
    ADD CONSTRAINT `chk_notification_deliveries_channel`
        CHECK (`channel` IN ('WEBSOCKET'));

-- 재시도 대상 스캔(status IN (...) AND next_retry_at <= NOW)
ALTER TABLE `notification_deliveries`
    ADD INDEX IF NOT EXISTS `idx_notification_deliveries_retry` (`status`, `next_retry_at`);
