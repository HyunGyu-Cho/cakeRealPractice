-- V17: 토스 실결제 전환 (스펙 docs/specs/order-payment.md)
-- 적용 전제: V1 ~ V16 순서대로 적용된 스키마
--
-- 실결제는 승인 결과가 리다이렉트 콜백으로 돌아오므로 결제가 두 요청으로 나뉜다.
-- 결제 시작 시점에 payments를 READY로 선삽입해 금액을 서버가 고정하고, 승인 성공 후에야
-- 주문을 만든다. 그래서 order_id가 잠시 비어 있는 구간이 생긴다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. payments.order_id NULL 허용
-- =========================================================
-- READY 행에는 아직 주문이 없다. active_paid_order_id는 status='DONE'일 때만 값을 갖는
-- 생성 열이므로(V4) NULL 허용으로 낮춰도 "주문당 활성 결제 1건" 보장은 그대로다.

ALTER TABLE `payments`
    MODIFY COLUMN `order_id` BIGINT NULL;

-- =========================================================
-- 2. webhook_events
-- =========================================================
-- 토스 전송 ID(event_id)를 UNIQUE로 잡아 재전송을 DB가 막는다. 수신은 저장만 하고
-- 즉시 200을 돌려주며(10초 제한), 결제 반영은 process_status로 따로 추적한다.

CREATE TABLE IF NOT EXISTS `webhook_events` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `event_id`        VARCHAR(100) NOT NULL,
    `event_type`      VARCHAR(50) NULL,
    `payment_key`     VARCHAR(200) NULL,
    `toss_order_id`   VARCHAR(100) NULL,
    `provider_status` VARCHAR(50) NULL,
    `payload`         TEXT NULL,
    `process_status`  VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    `fail_reason`     VARCHAR(500) NULL,
    `received_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `processed_at`    DATETIME(6) NULL,
    `created_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_webhook_events_event_id` UNIQUE (`event_id`),
    CONSTRAINT `chk_webhook_events_process_status`
        CHECK (`process_status` IN ('RECEIVED', 'PROCESSED', 'SKIPPED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 결제 키로 역추적(장애 조사) / 미처리 이벤트 순서대로 꺼내기
CREATE INDEX IF NOT EXISTS `idx_webhook_events_payment_key`
    ON `webhook_events` (`payment_key`);
CREATE INDEX IF NOT EXISTS `idx_webhook_events_process`
    ON `webhook_events` (`process_status`, `id`);
