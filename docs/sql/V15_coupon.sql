-- V15: 쿠폰 발급·적용 (스펙 docs/specs/coupon.md)
-- 적용 전제: V1 ~ V14 순서대로 적용된 스키마
--
-- V1이 coupons만 만들고 member_coupons(회원 지급)는 2차로 미뤄뒀다. 이번에 그 2차를 채운다.
--   1. coupons.status / discount_type 값 확정 + CHECK
--   2. member_coupons 생성 (V0 ERD 원안 + CHECK, 인덱스)
--   3. 데모 시드
--
-- 확정한 상태값 (conventions.md 인벤토리 갱신 완료)
--   coupons.status        ACTIVE / SUSPENDED / ENDED
--   member_coupons.status AVAILABLE / USED
-- "만료"와 "소진"은 expires_at·issued_quantity에서 나오는 파생값이라 저장하지 않는다.
-- 상태로 저장하면 시각이 지날 때마다 전 행을 갱신하는 배치가 필요하고, 배치가 밀리면 화면과 DB가 어긋난다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. coupons 제약
-- =========================================================

-- V1은 값 목록을 정하지 못해 VARCHAR(30)에 CHECK 없이 열어뒀다. 이제 확정했으므로
-- 상태 컬럼 규칙(VARCHAR(20) + chk_<table>_status)에 맞춘다.
ALTER TABLE `coupons`
    MODIFY COLUMN `status`        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN `discount_type` VARCHAR(20) NOT NULL;

ALTER TABLE `coupons`
    DROP CONSTRAINT IF EXISTS `chk_coupons_status`,
    ADD CONSTRAINT `chk_coupons_status`
        CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'ENDED')),
    DROP CONSTRAINT IF EXISTS `chk_coupons_discount_type`,
    ADD CONSTRAINT `chk_coupons_discount_type`
        CHECK (`discount_type` IN ('PERCENTAGE', 'FIXED_AMOUNT'));

-- 고객 다운로드 목록은 "지금 유효한 ACTIVE 쿠폰"을 기간으로 훑는다.
CREATE INDEX `idx_coupons_status_period` ON `coupons` (`status`, `starts_at`, `expires_at`);

-- =========================================================
-- 2. member_coupons
-- =========================================================

CREATE TABLE `member_coupons` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `coupon_id`        BIGINT NOT NULL,
    `member_id`        BIGINT NOT NULL,
    `status`           VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    `applied_order_id` BIGINT NULL,
    `issued_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `used_at`          DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    -- 1인 1장. 동시 다운로드 요청은 여기서 걸린다.
    CONSTRAINT `uk_member_coupons_coupon_member`
        UNIQUE (`coupon_id`, `member_id`),
    -- 주문 1건에 쿠폰 1장. 중복 사용의 최종 방어선이다.
    CONSTRAINT `uk_member_coupons_applied_order`
        UNIQUE (`applied_order_id`),
    CONSTRAINT `chk_member_coupons_status`
        CHECK (`status` IN ('AVAILABLE', 'USED')),
    CONSTRAINT `fk_member_coupons_coupon`
        FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`),
    CONSTRAINT `fk_member_coupons_member`
        FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
    CONSTRAINT `fk_member_coupons_applied_order`
        FOREIGN KEY (`applied_order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 쿠폰함·체크아웃 적용 가능 목록 조회
CREATE INDEX `idx_member_coupons_member_status`
    ON `member_coupons` (`member_id`, `status`);

-- =========================================================
-- 3. 데모 시드 (관리자 계정이 만든 것으로 둔다)
-- =========================================================

INSERT INTO `coupons`
    (`name`, `discount_type`, `discount_value`, `minimum_order_amount`,
     `maximum_discount_amount`, `total_quantity`, `starts_at`, `expires_at`,
     `status`, `created_by`)
SELECT '신규 가입 10% 할인', 'PERCENTAGE', 10.00, 20000,
       5000, 100, NOW(6) - INTERVAL 1 DAY, NOW(6) + INTERVAL 90 DAY,
       'ACTIVE', `m`.`id`
FROM `members` `m`
WHERE `m`.`email` = 'admin@cakeshop.local'
  AND NOT EXISTS (SELECT 1 FROM `coupons` `c` WHERE `c`.`name` = '신규 가입 10% 할인');

INSERT INTO `coupons`
    (`name`, `discount_type`, `discount_value`, `minimum_order_amount`,
     `maximum_discount_amount`, `total_quantity`, `starts_at`, `expires_at`,
     `status`, `created_by`)
SELECT '3,000원 픽업 할인', 'FIXED_AMOUNT', 3000.00, 15000,
       NULL, 50, NOW(6) - INTERVAL 1 DAY, NOW(6) + INTERVAL 30 DAY,
       'ACTIVE', `m`.`id`
FROM `members` `m`
WHERE `m`.`email` = 'admin@cakeshop.local'
  AND NOT EXISTS (SELECT 1 FROM `coupons` `c` WHERE `c`.`name` = '3,000원 픽업 할인');
