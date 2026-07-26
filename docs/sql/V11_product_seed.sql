-- 개발용 상품 샘플 시드
-- 적용 전제: V1 ~ V10 순서대로 적용된 스키마
--
-- products에 시드가 없어 로컬에서 장바구니 → 주문 → 결제 흐름을 확인할 수 없었다.
-- V1이 만든 카테고리에 붙는 최소 샘플만 넣는다. 실제 운영 데이터가 아니다.
-- 재실행해도 중복되지 않도록 이름으로 존재 여부를 확인한다.

SET NAMES utf8mb4;

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'NORMAL'),
       '딸기 생크림 케이크', '제철 딸기와 생크림을 올린 기본 홀케이크입니다.',
       38000, 'NORMAL', 2, 2, 20, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '딸기 생크림 케이크');

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SAME_DAY'),
       '초코 가나슈 케이크', '당일 픽업 가능한 진한 초콜릿 케이크입니다.',
       32000, 'NORMAL', 0, 1, 15, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '초코 가나슈 케이크');

INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SEASON'),
       '얼그레이 시즌 케이크', '시즌 한정 얼그레이 크림 케이크입니다.',
       41000, 'NORMAL', 3, 3, 8, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '얼그레이 시즌 케이크');

-- 품절 표시(파생값) 확인용 재고 0 상품
INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'NORMAL'),
       '말차 롤케이크', '재고 0 상태의 품절 표시 확인용 샘플입니다.',
       27000, 'NORMAL', 1, 1, 0, 'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '말차 롤케이크');

-- 판매 중지(status) 확인용 — 재고(품절)와 판매 스위치는 다른 축이다(conventions.md).
INSERT INTO `products`
    (`category_id`, `name`, `description`, `base_price`, `product_type`,
     `preparation_days`, `cancellation_limit_days`, `stock_quantity`, `status`)
SELECT (SELECT `id` FROM `categories` WHERE `code` = 'SEASON'),
       '한정 판매 종료 케이크', '판매 중지(INACTIVE) 상태 확인용 샘플입니다.',
       36000, 'NORMAL', 2, 2, 5, 'INACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `products` WHERE `name` = '한정 판매 종료 케이크');
