-- V6: product 도메인 착수 준비 (스펙 docs/specs/product.md)
--   1) products.status 확정(ACTIVE/INACTIVE): VARCHAR(20) + chk_products_status CHECK
--   2) products.stock_quantity 추가 — NULL = 재고 관리 안 함(주문제작 상품).
--      품절(0)·재고부족(1~4)은 이 값의 파생이며 별도 status로 저장하지 않는다.
--   3) product_images 테이블 추가(V0 정의 그대로) — 1차는 상품당 대표 1행만 사용
--   4) categories 기본 4행 시드 — 1차에서는 product_type과 코드 1:1

ALTER TABLE `products`
    DROP CONSTRAINT IF EXISTS `chk_products_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT `chk_products_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
    ADD COLUMN `stock_quantity` INT UNSIGNED NULL AFTER `cancellation_limit_days`;

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
