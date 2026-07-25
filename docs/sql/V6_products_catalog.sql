-- V6: product 도메인 착수 준비 (스펙 docs/specs/product.md)
--   1) products.status 확정(ACTIVE/INACTIVE): VARCHAR(20) + chk_products_status CHECK
--   2) products.stock_quantity 추가 — NULL = 재고 관리 안 함(주문제작 상품).
--      품절(0)·재고부족(1~4)은 이 값의 파생이며 별도 status로 저장하지 않는다.
--   3) product_images 테이블 추가(V0 정의 그대로) — 1차는 상품당 대표 1행만 사용
--   4) categories 기본 4행 시드 — 1차에서는 product_type과 코드 1:1
--
-- IF NOT EXISTS 를 붙인 이유: V0/V1은 확정 변경을 소급 반영하는 보관용 정본이라 stock_quantity가 이미 있다.
-- 신규 DB를 V0로 만든 뒤 증분 파일을 재적용하면 "Duplicate column name"으로 실패하고,
-- MariaDB의 단일 ALTER는 원자적이라 CHECK 재부착까지 통째로 롤백된다(실측 확인).

ALTER TABLE `products`
    DROP CONSTRAINT IF EXISTS `chk_products_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT `chk_products_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
    ADD COLUMN IF NOT EXISTS `stock_quantity` INT UNSIGNED NULL AFTER `cancellation_limit_days`;

CREATE TABLE IF NOT EXISTS `product_images` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `image_url`  VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_product_images_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- uk_categories_code 가 있어 재실행해도 중복 시드가 생기지 않는다.
INSERT IGNORE INTO `categories` (`code`, `name`, `sort_order`) VALUES
    ('NORMAL',   '일반 케이크', 1),
    ('CUSTOM',   '주문 제작',   2),
    ('SAME_DAY', '당일 픽업',   3),
    ('SEASON',   '시즌 상품',   4);
