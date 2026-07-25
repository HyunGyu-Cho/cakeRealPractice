-- 케이크 주문 서비스 ERD v3 수정안
-- Target DBMS: MariaDB 10.5+
--
-- ERD 이미지에는 데이터 타입과 상태(enum) 값이 표시되어 있지 않으므로
-- 컬럼명과 용도에 맞춰 타입, NULL 허용 여부, 기본값을 합리적으로 지정했다.
-- 애플리케이션의 Java enum이 확정되면 각 status 컬럼에 CHECK 제약을 추가한다.
--
-- payments.active_paid_order_id는 결제 상태가 DONE인 동안에만 order_id를 갖는
-- 생성 열이다. UNIQUE 제약과 결합하여 주문 하나에 활성 결제가 1건만 존재하게 한다.

SET NAMES utf8mb4;

-- =========================================================
-- 기존 객체 제거 (재실행 대비)
-- chat_rooms <-> chat_messages 순환 FK 때문에 DROP 순서로는
-- 해결할 수 없어 FOREIGN_KEY_CHECKS를 잠시 끈다.
-- =========================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `post_reports`;
DROP TABLE IF EXISTS `post_images`;
DROP TABLE IF EXISTS `post_likes`;
DROP TABLE IF EXISTS `comments`;
DROP TABLE IF EXISTS `posts`;
DROP TABLE IF EXISTS `post_categories`;
DROP TABLE IF EXISTS `store_holiday`;
DROP TABLE IF EXISTS `store_business_hour`;
DROP TABLE IF EXISTS `store`;
DROP TABLE IF EXISTS `notification_deliveries`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `customer_admin_notes`;
DROP TABLE IF EXISTS `chat_message_reads`;
DROP TABLE IF EXISTS `chat_message_attachments`;
DROP TABLE IF EXISTS `chat_room_orders`;
DROP TABLE IF EXISTS `chat_messages`;
DROP TABLE IF EXISTS `chat_rooms`;
DROP TABLE IF EXISTS `member_coupons`;
DROP TABLE IF EXISTS `coupons`;
DROP TABLE IF EXISTS `review_replies`;
DROP TABLE IF EXISTS `review_images`;
DROP TABLE IF EXISTS `reviews`;
DROP TABLE IF EXISTS `payment_cancellations`;
DROP TABLE IF EXISTS `payments`;
DROP TABLE IF EXISTS `order_item_images`;
DROP TABLE IF EXISTS `order_item_options`;
DROP TABLE IF EXISTS `order_items`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `cart_items`;
DROP TABLE IF EXISTS `carts`;
DROP TABLE IF EXISTS `product_images`;
DROP TABLE IF EXISTS `product_options`;
DROP TABLE IF EXISTS `product_option_groups`;
DROP TABLE IF EXISTS `products`;
DROP TABLE IF EXISTS `categories`;
DROP TABLE IF EXISTS `social_accounts`;
DROP TABLE IF EXISTS `members`;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 회원
-- =========================================================

CREATE TABLE `members` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `email`            VARCHAR(255) NOT NULL,
    `password`         VARCHAR(255) NULL,
    `nickname`         VARCHAR(50) NOT NULL,
    `phone`            VARCHAR(30) NULL,
    `role`             VARCHAR(30) NOT NULL DEFAULT 'USER',
    `status`           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `suspended_at`     DATETIME(6) NULL,
    `suspended_reason` VARCHAR(500) NULL,
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    `withdrawn_at`     DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_members_email` UNIQUE (`email`),
    CONSTRAINT `chk_members_status`
        CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `social_accounts` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `member_id`    BIGINT NOT NULL,
    `provider`     VARCHAR(30) NOT NULL,
    `provider_id`  VARCHAR(100) NOT NULL,
    `social_email` VARCHAR(255) NULL,
    `created_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_social_accounts_provider_id`
        UNIQUE (`provider`, `provider_id`),
    CONSTRAINT `fk_social_accounts_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 상품
-- =========================================================
    
CREATE TABLE `categories` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(50) NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `is_active`  TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_categories_code` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `products` (
    `id`                      BIGINT NOT NULL AUTO_INCREMENT,
    `category_id`             BIGINT NOT NULL,
    `name`                    VARCHAR(150) NOT NULL,
    `description`             TEXT NULL,
    `base_price`              DECIMAL(12, 0) NOT NULL,
    `product_type`            VARCHAR(30) NOT NULL,
    `preparation_days`        SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `cancellation_limit_days` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    -- NULL = 재고 관리 안 함(주문제작). 품절·재고부족은 이 값의 파생(별도 status 금지).
    `stock_quantity`          INT UNSIGNED NULL,
    `status`                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `average_rating`          DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    `review_count`            INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_products_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT `fk_products_category`
        FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_option_groups` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `product_id`     BIGINT NOT NULL,
    `name`           VARCHAR(100) NOT NULL,
    `required`       TINYINT(1) NOT NULL DEFAULT 0,
    `selection_type` VARCHAR(30) NOT NULL,
    `sort_order`     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_product_option_groups_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_options` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `option_group_id`  BIGINT NOT NULL,
    `name`             VARCHAR(100) NOT NULL,
    `additional_price` DECIMAL(12, 0) NOT NULL DEFAULT 0,
    `status`           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    `sort_order`       INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_product_options_group`
        FOREIGN KEY (`option_group_id`) REFERENCES `product_option_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_images` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `image_url`  VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_product_images_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 장바구니
-- =========================================================

CREATE TABLE `carts` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `member_id`  BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_carts_member` UNIQUE (`member_id`),
    CONSTRAINT `fk_carts_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `cart_items` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `cart_id`    BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `quantity`   INT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                               ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_cart_items_cart_product` UNIQUE (`cart_id`, `product_id`),
    CONSTRAINT `chk_cart_items_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `fk_cart_items_cart`
        FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`),
    CONSTRAINT `fk_cart_items_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 주문
-- =========================================================

CREATE TABLE `orders` (
    `id`                      BIGINT NOT NULL AUTO_INCREMENT,
    `order_number`            VARCHAR(50) NOT NULL,
    `member_id`               BIGINT NOT NULL,
    `orderer_name`            VARCHAR(50) NOT NULL,
    `orderer_phone`           VARCHAR(30) NOT NULL,
    `pickup_name`             VARCHAR(50) NOT NULL,
    `pickup_phone`            VARCHAR(30) NOT NULL,
    `original_amount`         DECIMAL(12, 0) NOT NULL,
    `discount_amount`         DECIMAL(12, 0) NOT NULL DEFAULT 0,
    `final_amount`            DECIMAL(12, 0) NOT NULL,
    `status`                  VARCHAR(20) NOT NULL,
    `pickup_at`               DATETIME(6) NOT NULL,
    `request_message`         TEXT NULL,
    `reject_reason`           TEXT NULL,
    `rejected_at`             DATETIME(6) NULL,
    `ready_at`                DATETIME(6) NULL,
    `picked_up_at`            DATETIME(6) NULL,
    `completed_at`            DATETIME(6) NULL,
    `canceled_at`             DATETIME(6) NULL,
    `cancel_reason`           TEXT NULL,
    `canceled_by`             VARCHAR(30) NULL,
    `pickup_reminder_sent_at` DATETIME(6) NULL,
    `created_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_orders_order_number` UNIQUE (`order_number`),
    CONSTRAINT `chk_orders_status`
        CHECK (`status` IN ('UNDER_REVIEW', 'IN_PRODUCTION', 'REJECTED',
                            'PAID', 'READY_FOR_PICKUP', 'PICKED_UP', 'CANCELED')),
    CONSTRAINT `fk_orders_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_items` (
    `id`                      BIGINT NOT NULL AUTO_INCREMENT,
    `order_id`                BIGINT NOT NULL,
    `product_id`              BIGINT NOT NULL,
    `product_name`            VARCHAR(150) NOT NULL,
    `product_type`            VARCHAR(30) NOT NULL,
    `quantity`                INT UNSIGNED NOT NULL,
    `base_price`              DECIMAL(12, 0) NOT NULL,
    `option_amount`           DECIMAL(12, 0) NOT NULL DEFAULT 0,
    `total_amount`            DECIMAL(12, 0) NOT NULL,
    `requirements`            TEXT NULL,
    `preparation_days`        SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `cancellation_limit_days` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
    CONSTRAINT `fk_order_items_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_item_options` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id`     BIGINT NOT NULL,
    `product_option_id` BIGINT NOT NULL,
    `option_group_name` VARCHAR(100) NOT NULL,
    `option_name`       VARCHAR(100) NOT NULL,
    `additional_price`  DECIMAL(12, 0) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_item_options_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `fk_order_item_options_product_option`
        FOREIGN KEY (`product_option_id`) REFERENCES `product_options` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_item_images` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `image_url`     VARCHAR(500) NOT NULL,
    `sort_order`    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_item_images_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 결제
-- =========================================================

CREATE TABLE `payments` (
    `id`                   BIGINT NOT NULL AUTO_INCREMENT,
    `order_id`             BIGINT NOT NULL,
    `toss_order_id`        VARCHAR(100) NOT NULL,
    `payment_key`          VARCHAR(200) NULL,
    `idempotency_key`      VARCHAR(100) NOT NULL,
    `method`               VARCHAR(30) NULL,
    `amount`               DECIMAL(12, 0) NOT NULL,
    `status`               VARCHAR(30) NOT NULL DEFAULT 'READY',
    `provider_status`      VARCHAR(50) NULL,
    `active_paid_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'DONE' THEN `order_id` ELSE NULL END
        ) STORED,
    `failure_code`         VARCHAR(100) NULL,
    `failure_message`      VARCHAR(500) NULL,
    `requested_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `approved_at`          DATETIME(6) NULL,
    `canceled_at`          DATETIME(6) NULL,
    `created_at`           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_payments_toss_order` UNIQUE (`toss_order_id`),
    CONSTRAINT `uk_payments_payment_key` UNIQUE (`payment_key`),
    CONSTRAINT `uk_payments_idempotency` UNIQUE (`idempotency_key`),
    CONSTRAINT `uk_payments_active_paid_order` UNIQUE (`active_paid_order_id`),
    CONSTRAINT `fk_payments_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_cancellations` (
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
    CONSTRAINT `uk_payment_cancellations_idempotency`
        UNIQUE (`idempotency_key`),
    CONSTRAINT `uk_payment_cancellations_transaction`
        UNIQUE (`transaction_key`),
    CONSTRAINT `fk_payment_cancellations_payment`
        FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 리뷰
-- =========================================================

CREATE TABLE `reviews` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id`  BIGINT NOT NULL,
    `product_id`     BIGINT NOT NULL,
    `member_id`      BIGINT NOT NULL,
    `overall_rating` TINYINT UNSIGNED NOT NULL,
    `taste_rating`   TINYINT UNSIGNED NOT NULL,
    `design_rating`  TINYINT UNSIGNED NOT NULL,
    `service_rating` TINYINT UNSIGNED NOT NULL,
    `content`        TEXT NULL,
    `status`         VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    `created_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT `uk_reviews_order_item` UNIQUE (`order_item_id`),
    CONSTRAINT `fk_reviews_order_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `fk_reviews_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
    CONSTRAINT `fk_reviews_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `review_images` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `review_id`  BIGINT NOT NULL,
    `image_url`  VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_review_images_review`
        FOREIGN KEY (`review_id`) REFERENCES `reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `review_replies` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `review_id`  BIGINT NOT NULL,
    `admin_id`   BIGINT NOT NULL,
    `content`    TEXT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_review_replies_review` UNIQUE (`review_id`),
    CONSTRAINT `fk_review_replies_review`
        FOREIGN KEY (`review_id`) REFERENCES `reviews` (`id`),
    CONSTRAINT `fk_review_replies_admin`
        FOREIGN KEY (`admin_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 쿠폰
-- =========================================================

CREATE TABLE `coupons` (
    `id`                      BIGINT NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(100) NOT NULL,
    `discount_type`           VARCHAR(30) NOT NULL,
    `discount_value`          DECIMAL(12, 2) NOT NULL,
    `minimum_order_amount`    DECIMAL(12, 0) NOT NULL DEFAULT 0,
    `maximum_discount_amount` DECIMAL(12, 0) NULL,
    `total_quantity`          INT UNSIGNED NOT NULL,
    `issued_quantity`         INT UNSIGNED NOT NULL DEFAULT 0,
    `starts_at`               DATETIME(6) NOT NULL,
    `expires_at`              DATETIME(6) NOT NULL,
    `status`                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    `created_by`              BIGINT NOT NULL,
    `created_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_coupons_creator`
        FOREIGN KEY (`created_by`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member_coupons` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `coupon_id`        BIGINT NOT NULL,
    `member_id`        BIGINT NOT NULL,
    `status`           VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    `applied_order_id` BIGINT NULL,
    `issued_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `used_at`          DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_member_coupons_coupon_member`
        UNIQUE (`coupon_id`, `member_id`),
    CONSTRAINT `uk_member_coupons_applied_order`
        UNIQUE (`applied_order_id`),
    CONSTRAINT `fk_member_coupons_coupon`
        FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`),
    CONSTRAINT `fk_member_coupons_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_member_coupons_applied_order`
        FOREIGN KEY (`applied_order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 채팅
-- =========================================================

CREATE TABLE `chat_rooms` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `customer_id`     BIGINT NOT NULL,
    `admin_id`        BIGINT NULL,
    `status`          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    `last_message_at` DATETIME(6) NULL,
    `created_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_rooms_customer` UNIQUE (`customer_id`),
    CONSTRAINT `fk_chat_rooms_customer`
        FOREIGN KEY (`customer_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_chat_rooms_admin`
        FOREIGN KEY (`admin_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_room_orders` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `chat_room_id` BIGINT NOT NULL,
    `order_id`     BIGINT NOT NULL,
    `created_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_room_orders_order` UNIQUE (`order_id`),
    CONSTRAINT `fk_chat_room_orders_room`
        FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`),
    CONSTRAINT `fk_chat_room_orders_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_messages` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `chat_room_id` BIGINT NOT NULL,
    `sender_id`    BIGINT NOT NULL,
    `message_type` VARCHAR(30) NOT NULL,
    `content`      TEXT NULL,
    `image_url`    VARCHAR(500) NULL,
    `created_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_chat_messages_room`
        FOREIGN KEY (`chat_room_id`) REFERENCES `chat_rooms` (`id`),
    CONSTRAINT `fk_chat_messages_sender`
        FOREIGN KEY (`sender_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_message_reads` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `chat_message_id` BIGINT NOT NULL,
    `member_id`       BIGINT NOT NULL,
    `read_at`         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_chat_message_reads_message_member`
        UNIQUE (`chat_message_id`, `member_id`),
    CONSTRAINT `fk_chat_message_reads_message`
        FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`),
    CONSTRAINT `fk_chat_message_reads_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 알림
-- =========================================================

CREATE TABLE `notifications` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `receiver_id`       BIGINT NOT NULL,
    `order_id`          BIGINT NULL,
    `chat_message_id`   BIGINT NULL,
    `notification_type` VARCHAR(50) NOT NULL,
    `title`             VARCHAR(200) NOT NULL,
    `content`           TEXT NOT NULL,
    `is_read`           TINYINT(1) NOT NULL DEFAULT 0,
    `read_at`           DATETIME(6) NULL,
    `target_url`        VARCHAR(500) NULL,
    `created_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_notifications_receiver`
        FOREIGN KEY (`receiver_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_notifications_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
    CONSTRAINT `fk_notifications_chat_message`
        FOREIGN KEY (`chat_message_id`) REFERENCES `chat_messages` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification_deliveries` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `notification_id`     BIGINT NOT NULL,
    `channel`             VARCHAR(30) NOT NULL,
    `recipient`           VARCHAR(500) NOT NULL,
    `template_code`       VARCHAR(100) NULL,
    `provider_message_id` VARCHAR(200) NULL,
    `status`              VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    `failure_code`        VARCHAR(100) NULL,
    `failure_reason`      VARCHAR(500) NULL,
    `requested_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `sent_at`             DATETIME(6) NULL,
    `delivered_at`        DATETIME(6) NULL,
    `clicked_at`          DATETIME(6) NULL,
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_notification_deliveries_notification`
        FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 매장
-- =========================================================

CREATE TABLE `store` (
    `id`                      BIGINT NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(100) NOT NULL,
    `description`             TEXT NULL,
    `image_url`               VARCHAR(500) NULL,
    `address`                 VARCHAR(500) NOT NULL,
    `phone`                   VARCHAR(30) NOT NULL,
    `pickup_place`            VARCHAR(200) NOT NULL,
    `pickup_start_time`       TIME NOT NULL,
    `pickup_end_time`         TIME NOT NULL,
    `pickup_interval_minutes` SMALLINT UNSIGNED NOT NULL,
    `created_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_store_pickup_time`
        CHECK (`pickup_start_time` < `pickup_end_time`),
    CONSTRAINT `chk_store_pickup_interval`
        CHECK (`pickup_interval_minutes` BETWEEN 10 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `store_business_hour` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `store_id`    BIGINT NOT NULL,
    `day_of_week` VARCHAR(10) NOT NULL,
    `open_time`   TIME NULL,
    `close_time`  TIME NULL,
    `is_closed`   TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_store_business_hour_day`
        UNIQUE (`store_id`, `day_of_week`),
    CONSTRAINT `fk_store_business_hour_store`
        FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `chk_store_business_hour_day`
        CHECK (`day_of_week` IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )),
    CONSTRAINT `chk_store_business_hour_time`
        CHECK (
            (`is_closed` = 1 AND `open_time` IS NULL AND `close_time` IS NULL)
            OR
            (`is_closed` = 0
                AND `open_time` IS NOT NULL
                AND `close_time` IS NOT NULL
                AND `open_time` < `close_time`)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `store_holiday` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `store_id`     BIGINT NOT NULL,
    `holiday_date` DATE NOT NULL,
    `reason`       VARCHAR(255) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_store_holiday_date`
        UNIQUE (`store_id`, `holiday_date`),
    CONSTRAINT `fk_store_holiday_store`
        FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 커뮤니티
-- =========================================================

CREATE TABLE `post_categories` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(50) NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `is_active`  TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_post_categories_code` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `posts` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `member_id`      BIGINT NOT NULL,
    `category_id`    BIGINT NOT NULL,
    `title`          VARCHAR(200) NOT NULL,
    `content`        TEXT NOT NULL,
    `view_count`     BIGINT NOT NULL DEFAULT 0,
    `like_count`     BIGINT NOT NULL DEFAULT 0,
    `status`         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `blocked_at`     DATETIME(6) NULL,
    `blocked_reason` VARCHAR(500) NULL,
    `blocked_by`     BIGINT NULL,
    `created_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_posts_status`
        CHECK (`status` IN ('ACTIVE', 'DELETED', 'BLOCKED')),
    CONSTRAINT `fk_posts_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_posts_category`
        FOREIGN KEY (`category_id`) REFERENCES `post_categories` (`id`),
    CONSTRAINT `fk_posts_blocked_by`
        FOREIGN KEY (`blocked_by`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comments` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`           BIGINT NOT NULL,
    `member_id`         BIGINT NOT NULL,
    `parent_comment_id` BIGINT NULL,
    `content`           TEXT NOT NULL,
    `status`            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `created_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_comments_status`
        CHECK (`status` IN ('ACTIVE', 'DELETED')),
    CONSTRAINT `fk_comments_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `fk_comments_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_comments_parent`
        FOREIGN KEY (`parent_comment_id`) REFERENCES `comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `post_likes` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT NOT NULL,
    `member_id`  BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_post_likes_post_member` UNIQUE (`post_id`, `member_id`),
    CONSTRAINT `fk_post_likes_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `fk_post_likes_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `post_images` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT NOT NULL,
    `image_url`  VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_post_images_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `post_reports` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`     BIGINT NOT NULL,
    `reporter_id` BIGINT NOT NULL,
    `reason`      VARCHAR(500) NOT NULL,
    `status`      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_post_reports_status`
        CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT `uk_post_reports_post_reporter`
        UNIQUE (`post_id`, `reporter_id`),
    CONSTRAINT `fk_post_reports_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
    CONSTRAINT `fk_post_reports_reporter`
        FOREIGN KEY (`reporter_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 필수 시드
-- =========================================================

-- 기본 카테고리 4종 (1차: product_type과 코드 1:1)
INSERT INTO `categories` (`code`, `name`, `sort_order`) VALUES
    ('NORMAL',   '일반 케이크', 1),
    ('CUSTOM',   '주문 제작',   2),
    ('SAME_DAY', '당일 픽업',   3),
    ('SEASON',   '시즌 상품',   4);

-- 공통 샘플 계정: 비밀번호는 둘 다 'Admin1234!' (BCrypt 해시 저장)
-- role 은 접두어 없는 값(USER/ADMIN)으로 저장 (MemberDetailsService 가 'ROLE_' 부착)
INSERT INTO `members` (`email`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
    ('admin@cakeshop.local',
    '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
    '관리자', '010-0000-0001', 'ADMIN', 'ACTIVE'),
    ('user@cakeshop.local',
    '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
    '테스트회원', '010-0000-0002', 'USER', 'ACTIVE');

-- 대표 매장 1행 (id = 1 = StoreService.DEFAULT_STORE_ID)
INSERT INTO `store`
    (`id`, `name`, `description`, `address`, `phone`,
    `pickup_place`, `pickup_start_time`, `pickup_end_time`, `pickup_interval_minutes`)
VALUES
    (1, '케이크 공방', '수제 케이크 전문 매장입니다.', '서울특별시 강남구 테헤란로 1', '02-000-0000',
    '매장 1층 픽업 데스크', '10:00:00', '20:00:00', 30);

-- 7개 요일 영업시간 (getStoreView 가 요일 행을 필수로 요구)
INSERT INTO `store_business_hour`
    (`store_id`, `day_of_week`, `open_time`, `close_time`, `is_closed`)
VALUES
    (1, 'MONDAY',    '10:00:00', '20:00:00', 0),
    (1, 'TUESDAY',   '10:00:00', '20:00:00', 0),
    (1, 'WEDNESDAY', '10:00:00', '20:00:00', 0),
    (1, 'THURSDAY',  '10:00:00', '20:00:00', 0),
    (1, 'FRIDAY',    '10:00:00', '20:00:00', 0),
    (1, 'SATURDAY',  '11:00:00', '21:00:00', 0),
    (1, 'SUNDAY',    NULL,       NULL,       1);
