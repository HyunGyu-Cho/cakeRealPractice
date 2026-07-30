-- =====================================================================
-- cake_performance 부하 테스트용 대량 시드
-- 전제: local 프로필로 앱을 1회 부팅해 Flyway(V1~V3)와 R__dev_seed가
--       cake_performance 에 적용된 상태 (post_categories 4행, products 6행 존재)
-- 적용: mysql -u<계정> -p cake_performance < perf/seed_load_data.sql
-- 재실행 가능: 기존 부하 데이터를 지우고 다시 넣는다 (개발 DB에는 절대 실행 금지)
-- =====================================================================

SET NAMES utf8mb4;

-- ---------- 0. 기존 부하 데이터 정리 (재실행 대비) ----------
DELETE FROM post_likes;
DELETE FROM comments;
DELETE FROM post_reports;
DELETE FROM posts;
DELETE FROM order_items;
DELETE FROM notifications WHERE receiver_id IN (SELECT id FROM members WHERE email LIKE 'loadtest%');
DELETE FROM orders;
DELETE FROM members WHERE email LIKE 'loadtest%';

-- ---------- 1. 부하용 회원 200명 (비밀번호는 시드 계정과 동일 해시 = Admin1234!) ----------
INSERT INTO members (email, password, nickname, role, status)
SELECT CONCAT('loadtest', seq, '@test.local'),
       '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
       CONCAT('부하유저', seq), 'USER', 'ACTIVE'
FROM seq_1_to_200;

SET @m0 = (SELECT MIN(id) FROM members WHERE email LIKE 'loadtest%');
SET @c0 = (SELECT MIN(id) FROM post_categories);
SET @p0 = (SELECT MIN(id) FROM products);

-- ---------- 2. 게시글 100,000건 (2%는 DELETED — 상태 필터 검증용) ----------
INSERT INTO posts (member_id, category_id, title, content, view_count, like_count, status, created_at)
SELECT @m0 + (seq % 200),
       @c0 + (seq % 4),
       CONCAT('부하테스트 게시글 ', seq, ' - 케이크 주문 후기와 질문'),
       REPEAT(CONCAT('본문 내용 ', seq, '. 케이크가 정말 맛있었어요. '), 20),
       FLOOR(RAND(42) * 1000),
       FLOOR(RAND(7) * 50),
       IF(seq % 50 = 0, 'DELETED', 'ACTIVE'),
       DATE_SUB(NOW(6), INTERVAL seq MINUTE)
FROM seq_1_to_100000;

SET @post0 = (SELECT MIN(id) FROM posts);

-- ---------- 3. 댓글 300,000건 ----------
INSERT INTO comments (post_id, member_id, content, status, created_at)
SELECT @post0 + (seq % 100000),
       @m0 + (seq % 200),
       CONCAT('댓글 ', seq, ' 잘 봤습니다!'),
       IF(seq % 40 = 0, 'DELETED', 'ACTIVE'),
       DATE_SUB(NOW(6), INTERVAL seq SECOND)
FROM seq_1_to_300000;

-- ---------- 4. 좋아요 100,000건 (post,member 유니크 조합) ----------
INSERT IGNORE INTO post_likes (post_id, member_id)
SELECT @post0 + (seq % 100000), @m0 + (seq % 200)
FROM seq_1_to_200000;

-- ---------- 5. 주문 100,000건 (최근 1년 분산 / PICKED_UP 70%, PAID 15%,
--             READY 5%, CANCELED 8%, REJECTED 2%) ----------
INSERT INTO orders (order_number, member_id, orderer_name, orderer_phone, pickup_name, pickup_phone,
                    original_amount, discount_amount, final_amount, status, pickup_at,
                    picked_up_at, canceled_at, created_at)
SELECT CONCAT('ORD', LPAD(seq, 10, '0')),
       @m0 + (seq % 200),
       CONCAT('주문자', seq % 200), '010-1111-2222',
       CONCAT('픽업', seq % 200), '010-1111-2222',
       30000 + (seq % 10) * 5000,
       IF(seq % 5 = 0, 3000, 0),
       30000 + (seq % 10) * 5000 - IF(seq % 5 = 0, 3000, 0),
       CASE WHEN seq % 100 < 70 THEN 'PICKED_UP'
            WHEN seq % 100 < 85 THEN 'PAID'
            WHEN seq % 100 < 90 THEN 'READY_FOR_PICKUP'
            WHEN seq % 100 < 98 THEN 'CANCELED'
            ELSE 'REJECTED' END,
       DATE_ADD(DATE_SUB(NOW(6), INTERVAL (seq % 365) DAY), INTERVAL (10 + seq % 10) HOUR),
       IF(seq % 100 < 70, DATE_SUB(NOW(6), INTERVAL (seq % 365) DAY), NULL),
       IF(seq % 100 >= 90 AND seq % 100 < 98, DATE_SUB(NOW(6), INTERVAL (seq % 365) DAY), NULL),
       DATE_SUB(NOW(6), INTERVAL (seq % 365) DAY)
FROM seq_1_to_100000;

-- ---------- 6. 주문 항목 (주문당 1건, 시드 상품 6종 순환) ----------
INSERT INTO order_items (order_id, product_id, product_name, product_type, quantity,
                         base_price, option_amount, total_amount, preparation_days, cancellation_limit_days)
SELECT o.id, @p0 + (o.id % 6), CONCAT('케이크상품', (o.id % 6) + 1), 'GENERAL',
       1 + (o.id % 2), 30000, 0, 30000 * (1 + (o.id % 2)), 1, 1
FROM orders o;

-- ---------- 확인 ----------
SELECT (SELECT COUNT(*) FROM posts)      AS posts,
       (SELECT COUNT(*) FROM comments)   AS comments,
       (SELECT COUNT(*) FROM post_likes) AS post_likes,
       (SELECT COUNT(*) FROM orders)     AS orders,
       (SELECT COUNT(*) FROM members WHERE email LIKE 'loadtest%') AS load_members;
