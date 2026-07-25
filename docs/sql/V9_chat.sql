-- 채팅 도메인: 고객별 영구 1:1 방, 메시지 멱등성, 읽음, 비공개 이미지
-- 적용 전제: V1 ~ V8 순서대로 적용된 스키마

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_rooms` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `customer_id`     BIGINT NOT NULL,
    `status`          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    `last_message_at` DATETIME(6) NULL,
    `closed_at`       DATETIME(6) NULL,
    `created_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_rooms_customer` UNIQUE (`customer_id`),
    CONSTRAINT `chk_chat_rooms_status`
        CHECK (`status` IN ('OPEN', 'CLOSED')),
    CONSTRAINT `fk_chat_rooms_customer`
        FOREIGN KEY (`customer_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 초기 ERD 골격(chat_rooms.admin_id, VARCHAR(30))이 이미 적용된 로컬 DB도
-- 운영 스키마로 제자리 업그레이드한다.
ALTER TABLE `chat_rooms`
    DROP FOREIGN KEY IF EXISTS `fk_chat_rooms_admin`,
    DROP COLUMN IF EXISTS `admin_id`,
    ADD COLUMN IF NOT EXISTS `closed_at` DATETIME(6) NULL AFTER `last_message_at`,
    ADD COLUMN IF NOT EXISTS `updated_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER `created_at`,
    DROP CONSTRAINT IF EXISTS `chk_chat_rooms_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD CONSTRAINT `chk_chat_rooms_status`
        CHECK (`status` IN ('OPEN', 'CLOSED'));

CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `chat_room_id`        BIGINT NOT NULL,
    `sender_id`           BIGINT NULL,
    `sender_type`         VARCHAR(20) NOT NULL,
    `message_type`        VARCHAR(20) NOT NULL,
    `content`             TEXT NULL,
    `image_key`           VARCHAR(500) NULL,
    `image_original_name` VARCHAR(255) NULL,
    `image_content_type`  VARCHAR(100) NULL,
    `image_size`          BIGINT NULL,
    `action_type`         VARCHAR(50) NULL,
    `action_url`          VARCHAR(500) NULL,
    `client_message_id`   CHAR(36) NULL,
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_messages_room_client`
        UNIQUE (`chat_room_id`, `client_message_id`),
    KEY `idx_chat_messages_room_id` (`chat_room_id`, `id`),
    CONSTRAINT `chk_chat_messages_sender_type`
        CHECK (`sender_type` IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT `chk_chat_messages_message_type`
        CHECK (`message_type` IN ('TEXT', 'IMAGE', 'SYSTEM_CARD')),
    CONSTRAINT `chk_chat_messages_sender`
        CHECK ((`sender_type` = 'SYSTEM' AND `sender_id` IS NULL)
            OR (`sender_type` <> 'SYSTEM' AND `sender_id` IS NOT NULL)),
    CONSTRAINT `fk_chat_messages_room`
        FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`),
    CONSTRAINT `fk_chat_messages_sender`
        FOREIGN KEY (`sender_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 초기 ERD 골격(sender_type/client UUID/비공개 이미지 메타데이터 없음)을
-- 기존 텍스트 이력을 보존하면서 확장한다.
ALTER TABLE `chat_messages`
    ADD COLUMN IF NOT EXISTS `sender_type` VARCHAR(20) NULL AFTER `sender_id`,
    ADD COLUMN IF NOT EXISTS `image_key` VARCHAR(500) NULL AFTER `content`,
    ADD COLUMN IF NOT EXISTS `image_original_name` VARCHAR(255) NULL AFTER `image_key`,
    ADD COLUMN IF NOT EXISTS `image_content_type` VARCHAR(100) NULL AFTER `image_original_name`,
    ADD COLUMN IF NOT EXISTS `image_size` BIGINT NULL AFTER `image_content_type`,
    ADD COLUMN IF NOT EXISTS `action_type` VARCHAR(50) NULL AFTER `image_size`,
    ADD COLUMN IF NOT EXISTS `action_url` VARCHAR(500) NULL AFTER `action_type`,
    ADD COLUMN IF NOT EXISTS `client_message_id` CHAR(36) NULL AFTER `action_url`;

UPDATE `chat_messages` message
JOIN `members` sender ON sender.id = message.sender_id
   SET message.sender_type = CASE
       WHEN sender.role = 'ADMIN' THEN 'ADMIN'
       ELSE 'CUSTOMER'
   END
 WHERE message.sender_type IS NULL;

ALTER TABLE `chat_messages`
    DROP COLUMN IF EXISTS `image_url`,
    DROP INDEX IF EXISTS `uk_chat_messages_room_client`,
    DROP CONSTRAINT IF EXISTS `chk_chat_messages_sender_type`,
    DROP CONSTRAINT IF EXISTS `chk_chat_messages_message_type`,
    DROP CONSTRAINT IF EXISTS `chk_chat_messages_sender`,
    MODIFY `sender_id` BIGINT NULL,
    MODIFY `sender_type` VARCHAR(20) NOT NULL,
    MODIFY `message_type` VARCHAR(20) NOT NULL,
    ADD CONSTRAINT `uk_chat_messages_room_client`
        UNIQUE (`chat_room_id`, `client_message_id`),
    ADD CONSTRAINT `chk_chat_messages_sender_type`
        CHECK (`sender_type` IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
    ADD CONSTRAINT `chk_chat_messages_message_type`
        CHECK (`message_type` IN ('TEXT', 'IMAGE', 'SYSTEM_CARD')),
    ADD CONSTRAINT `chk_chat_messages_sender`
        CHECK ((`sender_type` = 'SYSTEM' AND `sender_id` IS NULL)
            OR (`sender_type` <> 'SYSTEM' AND `sender_id` IS NOT NULL));

CREATE TABLE IF NOT EXISTS `chat_message_reads` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `chat_message_id` BIGINT NOT NULL,
    `member_id`       BIGINT NOT NULL,
    `read_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_message_reads_message_member`
        UNIQUE (`chat_message_id`, `member_id`),
    KEY `idx_chat_message_reads_member` (`member_id`, `chat_message_id`),
    CONSTRAINT `fk_chat_message_reads_message`
        FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`),
    CONSTRAINT `fk_chat_message_reads_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_room_orders` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `chat_room_id` BIGINT NOT NULL,
    `order_id`     BIGINT NOT NULL,
    `created_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_room_orders_order` UNIQUE (`order_id`),
    KEY `idx_chat_room_orders_room` (`chat_room_id`),
    CONSTRAINT `fk_chat_room_orders_room`
        FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`),
    CONSTRAINT `fk_chat_room_orders_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `notifications`
    ADD COLUMN IF NOT EXISTS `chat_message_id` BIGINT NULL AFTER `order_id`,
    DROP FOREIGN KEY IF EXISTS `fk_notifications_chat_message`,
    ADD CONSTRAINT `fk_notifications_chat_message`
        FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`);
