-- V7: cart 도메인 DB 전환 (스펙 docs/specs/cart.md)
-- 가격·상품 출력 정보·픽업 일시는 저장하지 않고 요청마다 product 공개 계약으로 재검증한다.

CREATE TABLE IF NOT EXISTS `carts` (
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

CREATE TABLE IF NOT EXISTS `cart_items` (
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

-- V0 구버전으로 만든 로컬 DB도 같은 최종 구조가 되도록 보정한다.
ALTER TABLE `carts`
    ADD UNIQUE INDEX IF NOT EXISTS `uk_carts_member` (`member_id`);

ALTER TABLE `cart_items`
    DROP COLUMN IF EXISTS `requirements`,
    ADD UNIQUE INDEX IF NOT EXISTS `uk_cart_items_cart_product` (`cart_id`, `product_id`),
    DROP CONSTRAINT IF EXISTS `chk_cart_items_quantity`,
    ADD CONSTRAINT `chk_cart_items_quantity` CHECK (`quantity` > 0);
