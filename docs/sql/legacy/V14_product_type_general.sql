-- V14: 상품 유형 NORMAL -> GENERAL 이름 변경
-- 적용 전제: V1 ~ V13 순서대로 적용된 스키마
--   (V13은 PR #16 주문제작 스펙에서 추가된다. 두 PR이 병합 순서와 무관하게 적용되도록
--    이 파일은 V13의 객체를 참조하지 않는다.)
--
-- 팀이 확정한 ProductType 이름은 GENERAL이지만 코드·시드는 NORMAL로 들어가 있었다.
-- 값 개수는 4개 그대로이고 NORMAL 하나만 GENERAL로 바꾼다.
--
-- product_type을 들고 있는 곳은 세 군데다.
--   - categories.code       : 카테고리 코드 (product_type과 1:1)
--   - products.product_type : 상품 유형
--   - order_items.product_type : 주문 시점 스냅샷
-- 스냅샷까지 함께 바꾸는 이유는 OrderMapper의 "일반 주문만 조회" 조건이
-- order_items.product_type = 'GENERAL'을 보기 때문이다. 남겨두면 기존 주문이 조회에서 빠진다.

SET NAMES utf8mb4;

UPDATE `categories`  SET `code` = 'GENERAL'         WHERE `code` = 'NORMAL';
UPDATE `products`    SET `product_type` = 'GENERAL' WHERE `product_type` = 'NORMAL';
UPDATE `order_items` SET `product_type` = 'GENERAL' WHERE `product_type` = 'NORMAL';

-- products.product_type에는 CHECK가 없어 어떤 문자열이든 들어갈 수 있었다.
-- 이번에 값을 정리하면서 제약을 걸어 NORMAL이 다시 새어 들어오지 못하게 한다.
ALTER TABLE `products`
    DROP CONSTRAINT IF EXISTS `chk_products_type`,
    ADD CONSTRAINT `chk_products_type`
        CHECK (`product_type` IN ('GENERAL', 'CUSTOM', 'SAME_DAY', 'SEASON'));

-- order_items.product_type에는 CHECK를 걸지 않는다. 주문 시점 스냅샷이라
-- 나중에 상품 유형이 추가·변경되면 과거 주문까지 마이그레이션해야 하기 때문이다.
