-- V16: 후기 (스펙 docs/specs/review.md)
-- 적용 전제: V1 ~ V15 순서대로 적용된 스키마
--
-- reviews·review_images·review_replies는 V0 ERD에 설계만 있고 생성된 적이 없다
-- (V13이 order_item_options·order_item_images에 한 것과 같은 상황).
--
-- V0_ERD 원안에서 바꾼 것 하나: 세부 평점 3축(맛·디자인·응대)을 NULL 허용으로 낮춘다.
-- 네 축을 모두 채우도록 강제하면 작성률이 떨어진다. 종합 평점만 필수이며,
-- products.average_rating 집계도 종합 평점 기준이다. (V0_ERD.sql에 소급 반영)
--
-- CREATE TABLE IF NOT EXISTS + 뒤이은 ALTER 형태인 이유는 V15와 같다 —
-- V0_ERD.sql을 통째로 적용해 둔 DB에는 이 테이블들이 제약 없이 이미 있을 수 있다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. reviews
-- =========================================================

CREATE TABLE IF NOT EXISTS `reviews` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id`  BIGINT NOT NULL,
    `product_id`     BIGINT NOT NULL,
    `member_id`      BIGINT NOT NULL,
    `overall_rating` TINYINT UNSIGNED NOT NULL,
    `taste_rating`   TINYINT UNSIGNED NULL,
    `design_rating`  TINYINT UNSIGNED NULL,
    `service_rating` TINYINT UNSIGNED NULL,
    `content`        TEXT NULL,
    `status`         VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    `created_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    -- 주문 항목당 후기 1개. 회원·상품당이 아니라 항목당이라 같은 상품을 여러 번 사면 각각 쓸 수 있다.
    CONSTRAINT `uk_reviews_order_item` UNIQUE (`order_item_id`),
    CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT `fk_reviews_order_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `fk_reviews_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
    CONSTRAINT `fk_reviews_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V0_ERD로 먼저 만들어진 경우를 위 정의와 같은 모양으로 맞춘다(새로 만든 경우엔 변화 없음).
ALTER TABLE `reviews`
    MODIFY COLUMN `taste_rating`   TINYINT UNSIGNED NULL,
    MODIFY COLUMN `design_rating`  TINYINT UNSIGNED NULL,
    MODIFY COLUMN `service_rating` TINYINT UNSIGNED NULL;

ALTER TABLE `reviews`
    DROP CONSTRAINT IF EXISTS `chk_reviews_status`,
    ADD CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('VISIBLE', 'HIDDEN'));

-- 평점 범위는 앱이 아니라 DB가 지킨다. NULL은 "입력하지 않음"이라 CHECK를 통과한다.
ALTER TABLE `reviews`
    DROP CONSTRAINT IF EXISTS `chk_reviews_overall_rating`,
    ADD CONSTRAINT `chk_reviews_overall_rating` CHECK (`overall_rating` BETWEEN 1 AND 5),
    DROP CONSTRAINT IF EXISTS `chk_reviews_taste_rating`,
    ADD CONSTRAINT `chk_reviews_taste_rating`   CHECK (`taste_rating`   BETWEEN 1 AND 5),
    DROP CONSTRAINT IF EXISTS `chk_reviews_design_rating`,
    ADD CONSTRAINT `chk_reviews_design_rating`  CHECK (`design_rating`  BETWEEN 1 AND 5),
    DROP CONSTRAINT IF EXISTS `chk_reviews_service_rating`,
    ADD CONSTRAINT `chk_reviews_service_rating` CHECK (`service_rating` BETWEEN 1 AND 5);

-- 상품 상세 후기 목록 / 내 후기함
CREATE INDEX IF NOT EXISTS `idx_reviews_product_status` ON `reviews` (`product_id`, `status`, `id`);
CREATE INDEX IF NOT EXISTS `idx_reviews_member` ON `reviews` (`member_id`, `id`);

-- =========================================================
-- 2. review_images
-- =========================================================

CREATE TABLE IF NOT EXISTS `review_images` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `review_id`  BIGINT NOT NULL,
    `image_url`  VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_review_images_review`
        FOREIGN KEY (`review_id`) REFERENCES `reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS `idx_review_images_review`
    ON `review_images` (`review_id`, `sort_order`);

-- =========================================================
-- 3. review_replies (후기당 답글 1개)
-- =========================================================

CREATE TABLE IF NOT EXISTS `review_replies` (
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
