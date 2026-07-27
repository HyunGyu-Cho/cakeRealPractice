-- V13: 주문제작 요청·견적·결제 링크 운영 스키마
-- 적용 전제: V1 ~ V12 순서대로 적용된 스키마
-- 스펙: docs/specs/order-custom.md
--
-- 설계 요지
--   - 요청서 전용 테이블을 만들지 않는다. V0 ERD가 이미 주문제작을 orders 계열에 담도록 설계했다.
--     요청서 = orders(UNDER_REVIEW) 1행 + order_items 1행 + order_item_options N행 + order_item_images N행.
--     반려 사유는 기존 orders.reject_reason, 요구사항은 기존 order_items.requirements를 쓴다.
--   - orders.status(확정 7개)는 값도 CHECK도 건드리지 않는다. 견적 진행은 custom_order_quotes.status가 소유한다.
--   - order_item_options / order_item_images는 V0_ERD.sql에 설계만 있고 생성된 적이 없어 여기서 실제로 만든다
--     (V12가 notification_deliveries에 한 것과 같은 상황). 컬럼은 ERD 원안 그대로다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. ERD 설계만 있던 주문 항목 하위 테이블 생성
-- =========================================================

CREATE TABLE IF NOT EXISTS `order_item_options` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id`     BIGINT NOT NULL,
    `product_option_id` BIGINT NOT NULL,
    -- 주문 시점 스냅샷. 이후 상품 옵션이 바뀌어도 주문 내역은 그대로 남는다.
    `option_group_name` VARCHAR(100) NOT NULL,
    `option_name`       VARCHAR(100) NOT NULL,
    `additional_price`  DECIMAL(12, 0) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    -- order_item_id 인덱스는 FK가 자동 생성하므로 따로 두지 않는다
    CONSTRAINT `fk_order_item_options_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `fk_order_item_options_product_option`
        FOREIGN KEY (`product_option_id`) REFERENCES `product_options` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `order_item_images` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    -- FileStorageClient가 저장한 경로. 최대 3장(서비스가 검증).
    `image_url`     VARCHAR(500) NOT NULL,
    `sort_order`    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_item_images_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 2. 견적 (회차 누적)
-- =========================================================

-- 재견적 시 이전 행을 SUPERSEDED로 내리고 version + 1 행을 새로 넣는다. 행을 수정하지 않아
-- 이전 견적 금액·제작 가능일 스냅샷이 그대로 남는다.
CREATE TABLE IF NOT EXISTS `custom_order_quotes` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `order_id`         BIGINT NOT NULL,
    `version`          INT NOT NULL,
    `quoted_amount`    DECIMAL(12, 0) NOT NULL,
    `producible_date`  DATE NOT NULL,          -- 관리자가 제시하는 제작 가능일(픽업 하한)
    `admin_note`       VARCHAR(500) NULL,
    `issued_by`        BIGINT NOT NULL,        -- 견적을 발행한 관리자 회원
    `status`           VARCHAR(20) NOT NULL DEFAULT 'SENT',
    `sent_at`          DATETIME(6) NOT NULL,
    `accepted_at`      DATETIME(6) NULL,
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    -- 재견적이 동시에 발행돼도 회차가 겹치지 않도록 DB가 막는다
    CONSTRAINT `uk_custom_order_quotes_version` UNIQUE (`order_id`, `version`),
    KEY `idx_custom_order_quotes_order` (`order_id`, `version` DESC),
    CONSTRAINT `chk_custom_order_quotes_status`
        CHECK (`status` IN ('SENT', 'ACCEPTED', 'SUPERSEDED')),
    CONSTRAINT `fk_custom_order_quotes_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
    CONSTRAINT `fk_custom_order_quotes_issuer`
        FOREIGN KEY (`issued_by`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 3. 결제 링크 (일회성 토큰)
-- =========================================================

-- 중복 결제 방지 1차 방어선이 uk_custom_order_payment_links_quote(견적당 1건)이고,
-- 2차는 결제 트랜잭션의 SELECT ... FOR UPDATE + status 재확인,
-- 3차는 payments.idempotency_key(토큰) UNIQUE 충돌이다. 스펙 6장 규칙 5.
CREATE TABLE IF NOT EXISTS `custom_order_payment_links` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `quote_id`    BIGINT NOT NULL,
    `token`       VARCHAR(64) NOT NULL,
    `amount`      DECIMAL(12, 0) NOT NULL,     -- 발급 시점 견적 금액 스냅샷
    `expires_at`  DATETIME(6) NOT NULL,        -- 발급 후 72시간과 제작 가능일 전날 중 이른 쪽
    `used_at`     DATETIME(6) NULL,
    `revoked_at`  DATETIME(6) NULL,            -- 재견적·주문 취소로 무효화된 시각
    `status`      VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    `created_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_custom_order_payment_links_token` UNIQUE (`token`),
    CONSTRAINT `uk_custom_order_payment_links_quote` UNIQUE (`quote_id`),
    KEY `idx_custom_order_payment_links_expiry` (`status`, `expires_at`),
    CONSTRAINT `chk_custom_order_payment_links_status`
        CHECK (`status` IN ('ISSUED', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT `fk_custom_order_payment_links_quote`
        FOREIGN KEY (`quote_id`) REFERENCES `custom_order_quotes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- 4. 기존 테이블 보강
-- =========================================================

-- 고객 희망 예산. 수제 전용이라 NULL 허용이며 일반 주문 흐름은 읽지도 쓰지도 않는다.
ALTER TABLE `orders`
    ADD COLUMN IF NOT EXISTS `desired_budget` DECIMAL(12, 0) NULL AFTER `final_amount`;

-- product_options.status: 상태 컬럼 규칙(VARCHAR(20) + CHECK) 적용.
-- product 스펙이 "옵션 관리는 order(수제) 차례"로 넘긴 ☐ 항목을 여기서 확정한다.
ALTER TABLE `product_options`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE `product_options`
    DROP CONSTRAINT IF EXISTS `chk_product_options_status`,
    ADD CONSTRAINT `chk_product_options_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE'));

-- =========================================================
-- 5. 주문제작 상품과 옵션 시드 (개발용)
-- =========================================================
-- 목업 custom-option.html이 하드코딩하던 크기·맛·색상을 DB 정본으로 옮긴다.
-- V11과 같이 이름으로 존재 여부를 확인해 재실행해도 중복되지 않는다.

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'CUSTOM'),
       '레터링 주문 케이크', '원하는 문구와 디자인으로 제작하는 주문 케이크입니다. 관리자 견적 후 결제합니다.',
       55000, 'CUSTOM', 5, 3, NULL, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '레터링 주문 케이크');

INSERT INTO `product_option_groups` (`product_id`, `name`, `required`, `selection_type`, `sort_order`)
SELECT p.`id`, '크기', 1, 'SINGLE', 1
  FROM `products` p
 WHERE p.`name` = '레터링 주문 케이크'
   AND NOT EXISTS (SELECT 1 FROM `product_option_groups` g
                    WHERE g.`product_id` = p.`id` AND g.`name` = '크기');

INSERT INTO `product_option_groups` (`product_id`, `name`, `required`, `selection_type`, `sort_order`)
SELECT p.`id`, '맛', 1, 'SINGLE', 2
  FROM `products` p
 WHERE p.`name` = '레터링 주문 케이크'
   AND NOT EXISTS (SELECT 1 FROM `product_option_groups` g
                    WHERE g.`product_id` = p.`id` AND g.`name` = '맛');

INSERT INTO `product_option_groups` (`product_id`, `name`, `required`, `selection_type`, `sort_order`)
SELECT p.`id`, '색상', 1, 'SINGLE', 3
  FROM `products` p
 WHERE p.`name` = '레터링 주문 케이크'
   AND NOT EXISTS (SELECT 1 FROM `product_option_groups` g
                    WHERE g.`product_id` = p.`id` AND g.`name` = '색상');

-- 옵션 값: (그룹명, 옵션명, 추가금액, 정렬순서)
INSERT INTO `product_options` (`option_group_id`, `name`, `additional_price`, `status`, `sort_order`)
SELECT g.`id`, v.`name`, v.`price`, 'ACTIVE', v.`sort_order`
  FROM `product_option_groups` g
  JOIN `products` p ON p.`id` = g.`product_id`
  JOIN (
        SELECT '크기' AS `group_name`, '1호 (10cm)'  AS `name`,     0 AS `price`, 1 AS `sort_order` UNION ALL
        SELECT '크기', '2호 (15cm)',  5000, 2 UNION ALL
        SELECT '크기', '3호 (21cm)', 10000, 3 UNION ALL
        SELECT '맛',   '바닐라',         0, 1 UNION ALL
        SELECT '맛',   '초코',        2000, 2 UNION ALL
        SELECT '맛',   '딸기',        2000, 3 UNION ALL
        SELECT '맛',   '녹차',        3000, 4 UNION ALL
        SELECT '색상', '화이트',         0, 1 UNION ALL
        SELECT '색상', '아이보리',       0, 2 UNION ALL
        SELECT '색상', '핑크',        2000, 3 UNION ALL
        SELECT '색상', '민트',        2000, 4
       ) v ON v.`group_name` = g.`name`
 WHERE p.`name` = '레터링 주문 케이크'
   AND NOT EXISTS (SELECT 1 FROM `product_options` o
                    WHERE o.`option_group_id` = g.`id` AND o.`name` = v.`name`);
