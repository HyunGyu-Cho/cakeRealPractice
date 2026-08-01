-- 좋아요 동시성 결함(이슈 #51) 재현용 시드 — 개발 DB(cakeshop)에 적용해도 안전하다.
--
-- seed_load_data.sql 과 달리 대량 데이터가 아니다. 필요한 것은
-- "로그인 가능한 회원 N명"과 "그들이 동시에 좋아요를 누를 글 1개"뿐이다.
-- 재실행 가능하며, 매 실행마다 좋아요 상태를 초기화한다.
--
-- 사용: mysql -uroot -p cakeshop < perf/seed_like_storm.sql
--       그다음 python perf/like_storm.py 20 60

SET NAMES utf8mb4;

-- 1) 부하용 회원 30명 — 비밀번호는 R__dev_seed 와 같은 'Admin1234!' 해시
INSERT IGNORE INTO `members` (`email`, `password`, `nickname`, `phone`, `role`, `status`)
SELECT CONCAT('storm', seq - 1, '@cakeshop.local'),
       '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
       CONCAT('부하회원', seq),
       '010-0000-0000',
       'USER',
       'ACTIVE'
  FROM seq_1_to_30;

-- 2) 좋아요가 몰릴 글 1개 — 제목의 '동시성' 으로 like_storm.py 가 찾는다
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`)
SELECT (SELECT `id` FROM `members` WHERE `email` = 'storm0@cakeshop.local'),
       (SELECT MIN(`id`) FROM `post_categories`),
       '동시성 재현용 인기 글',
       '이 글 하나에 좋아요를 몰아쳐 이슈 #51 을 재현한다.',
       'ACTIVE'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `posts` WHERE `title` = '동시성 재현용 인기 글');

-- 3) 좋아요 상태 초기화 — 매 측정을 같은 조건에서 시작한다
DELETE FROM `post_likes`
 WHERE `post_id` = (SELECT `id` FROM `posts` WHERE `title` = '동시성 재현용 인기 글');

UPDATE `posts`
   SET `like_count` = 0
 WHERE `title` = '동시성 재현용 인기 글';

SELECT `id` AS `부하대상_글_id`, `title`, `like_count`
  FROM `posts`
 WHERE `title` = '동시성 재현용 인기 글';
