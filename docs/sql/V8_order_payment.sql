-- V8: 일반 주문·모의 결제·전액 취소/환불 운영 스키마
-- 적용 순서: V0/V1 정본 또는 V1~V7 적용 후 실행한다.

CREATE TABLE IF NOT EXISTS `payment_cancellations` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `payment_id`       BIGINT NOT NULL,
    `idempotency_key`  VARCHAR(100) NOT NULL,
    `cancel_amount`    DECIMAL(12, 0) NOT NULL,
    `cancel_reason`    VARCHAR(500) NOT NULL,
    `status`           VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    `transaction_key`  VARCHAR(200) NULL,
    `failure_code`     VARCHAR(100) NULL,
    `failure_message`  VARCHAR(500) NULL,
    `requested_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `canceled_at`      DATETIME(6) NULL,
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_payment_cancellations_idempotency` UNIQUE (`idempotency_key`),
    CONSTRAINT `uk_payment_cancellations_transaction` UNIQUE (`transaction_key`),
    CONSTRAINT `chk_payment_cancellations_status`
        CHECK (`status` IN ('REQUESTED', 'DONE', 'REJECTED')),
    CONSTRAINT `fk_payment_cancellations_payment`
        FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`),
    INDEX `idx_payment_cancellations_payment_created` (`payment_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `payments`
    DROP CONSTRAINT IF EXISTS `chk_payments_status`,
    ADD CONSTRAINT `chk_payments_status`
        CHECK (`status` IN ('READY', 'DONE', 'CANCELED',
                            'PARTIAL_CANCELED', 'ABORTED', 'EXPIRED'));

ALTER TABLE `payment_cancellations`
    DROP CONSTRAINT IF EXISTS `chk_payment_cancellations_status`,
    ADD CONSTRAINT `chk_payment_cancellations_status`
        CHECK (`status` IN ('REQUESTED', 'DONE', 'REJECTED'));

CREATE INDEX IF NOT EXISTS `idx_orders_member_created`
    ON `orders` (`member_id`, `created_at`);
CREATE INDEX IF NOT EXISTS `idx_orders_status_pickup`
    ON `orders` (`status`, `pickup_at`);
CREATE INDEX IF NOT EXISTS `idx_orders_pickup`
    ON `orders` (`pickup_at`);
CREATE INDEX IF NOT EXISTS `idx_order_items_order`
    ON `order_items` (`order_id`);
CREATE INDEX IF NOT EXISTS `idx_payments_status_approved`
    ON `payments` (`status`, `approved_at`);
CREATE INDEX IF NOT EXISTS `idx_payments_order`
    ON `payments` (`order_id`);
CREATE INDEX IF NOT EXISTS `idx_payment_cancellations_payment_created`
    ON `payment_cancellations` (`payment_id`, `created_at`);
