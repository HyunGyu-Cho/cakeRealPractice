-- V18: 개발용 시드 (현행 스키마 기준)
--
-- [배경]
--   빈 DB에 V0_ERD.sql(전체 스펙 정본)만 적용하면 게시판 카테고리와 상품이 비어 있어
--   커뮤니티·장바구니·주문·주문제작 화면을 로컬에서 확인할 수 없다.
--   기존 시드는 V2(게시판)·V11(상품)에 흩어져 있는데, V11은 V14의 카테고리 코드 변경
--   (NORMAL → GENERAL) 이전에 작성돼 현행 스키마에서는 category_id를 찾지 못한다.
--   그래서 현행 스키마 기준으로 개발용 시드를 여기 한 파일에 모은다.
--
-- [적용 전제]
--   V0_ERD.sql(또는 V1 + 증분 전체)이 적용된 스키마.
--   이미 V2·V11을 적용한 기존 DB에 얹어도 안전하도록 전부 멱등하게 작성한다.
--
-- 실제 운영 데이터가 아니라 화면·플로우 확인용 샘플이다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. 게시판 카테고리 (커뮤니티 글 작성의 전제)
-- =========================================================

INSERT IGNORE INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`) VALUES
    ('REVIEW',   '후기',   1, 1),
    ('QUESTION', '질문',   1, 2),
    ('RECIPE',   '레시피', 1, 3),
    ('FREE',     '자유',   1, 4);

-- =========================================================
-- 2. 일반 상품 샘플
-- =========================================================
-- 카테고리 코드는 V0 정본 기준(GENERAL / SAME_DAY / SEASON)이다.
-- 재실행해도 중복되지 않도록 이름으로 존재 여부를 확인한다.

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'GENERAL'),
       '딸기 생크림 케이크', '제철 딸기와 생크림을 올린 기본 홀케이크입니다.',
       38000, 'GENERAL', 2, 2, 20, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '딸기 생크림 케이크');

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SAME_DAY'),
       '초코 가나슈 케이크', '당일 픽업 가능한 진한 초콜릿 케이크입니다.',
       32000, 'GENERAL', 0, 1, 15, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '초코 가나슈 케이크');

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SEASON'),
       '얼그레이 시즌 케이크', '시즌 한정 얼그레이 크림 케이크입니다.',
       41000, 'GENERAL', 3, 3, 8, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '얼그레이 시즌 케이크');

-- 품절 표시(파생값) 확인용 재고 0 상품
INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'GENERAL'),
       '말차 롤케이크', '재고 0 상태의 품절 표시 확인용 샘플입니다.',
       27000, 'GENERAL', 1, 1, 0, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '말차 롤케이크');

-- 판매 중지(status) 확인용 — 재고(품절)와 판매 스위치는 다른 축이다(conventions.md).
INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SEASON'),
       '한정 판매 종료 케이크', '판매 중지(INACTIVE) 상태 확인용 샘플입니다.',
       36000, 'GENERAL', 2, 2, 5, 'INACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '한정 판매 종료 케이크');

-- =========================================================
-- 3. 주문제작 상품 샘플
-- =========================================================
-- 주문제작은 재고를 관리하지 않으므로 stock_quantity는 NULL이다(0이 아니다).
-- 0으로 넣으면 품절 파생값이 참이 되어 요청 접수가 CUSTOM_002로 막힌다.

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'CUSTOM'),
       '주문제작 케이크', '디자인·문구를 협의해 제작하는 주문제작 상품입니다.',
       60000, 'CUSTOM', 3, 3, NULL, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '주문제작 케이크');
