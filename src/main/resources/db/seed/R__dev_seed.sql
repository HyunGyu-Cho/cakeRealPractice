-- R__dev_seed: 개발용 샘플 데이터 (local 프로필 전용)
--
-- [왜 db/migration이 아닌가]
--   실제 운영 데이터가 아니라 화면·플로우 확인용 더미다. 공용 RDS에 들어가면 안 되므로
--   db/migration과 분리하고, local 프로필에서만 flyway.locations에 추가한다(application.yml).
--   운영에도 필요한 코드값(카테고리·관리자 계정·매장)은 V1·V2가 담당한다.
--
-- [왜 R__ (repeatable) 인가]
--   버전 마이그레이션과 달리 파일이 바뀌면 다시 실행된다. 샘플을 늘리거나 고칠 때
--   새 V번호를 붙이지 않아도 되고, 아래처럼 전부 멱등하게 작성했으므로 재실행이 안전하다.
--   repeatable은 모든 버전 마이그레이션이 끝난 뒤 실행되므로 스키마는 항상 최신이다.
--
-- [멱등성]
--   이미 상품이 들어 있는 기존 로컬 DB에 얹어도 중복되지 않도록 이름으로 존재를 확인한다.

SET NAMES utf8mb4;

-- =========================================================
-- 1. 공통 샘플 계정
-- =========================================================
-- 비밀번호는 둘 다 'Admin1234!' (BCrypt 해시 저장).
-- 해시가 저장소에 공개돼 있으므로 공용 RDS에는 절대 넣지 않는다. 그래서 베이스라인 스키마가
-- 아니라 local 전용 시드에 둔다. RDS 관리자 계정은 별도로 만든다.
-- role은 접두어 없는 값(USER/ADMIN)으로 저장한다(MemberDetailsService가 'ROLE_' 부착).

INSERT IGNORE INTO `members` (`email`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
    ('admin@cakeshop.local',
    '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
    '관리자', '010-0000-0001', 'ADMIN', 'ACTIVE'),
    ('user@cakeshop.local',
    '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
    '테스트회원', '010-0000-0002', 'USER', 'ACTIVE');

-- =========================================================
-- 2. 일반 상품 샘플
-- =========================================================
-- 카테고리 코드는 베이스라인 기준(GENERAL / SAME_DAY / SEASON)이다.

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
