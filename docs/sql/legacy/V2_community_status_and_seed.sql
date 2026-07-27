-- V2: community·review 상태값 확정 + 커뮤니티 개발용 시드 (현규)
--
-- [상태값 확정 — conventions.md 인벤토리의 ☐ 항목 채움]
--   posts.status        : ACTIVE / DELETED / BLOCKED   (기본 ACTIVE, 기존 PUBLISHED → ACTIVE 전환)
--   comments.status     : ACTIVE / DELETED             (기본 ACTIVE, 기존 PUBLISHED → ACTIVE 전환)
--   post_reports.status : PENDING / ACCEPTED / REJECTED (기본 PENDING 유지)
--   reviews.status      : VISIBLE / HIDDEN             (기본 VISIBLE 유지)
-- DB 컬럼 규칙(전원 합의)에 맞춰 VARCHAR(20) NOT NULL + chk_<table>_status CHECK + DEFAULT 적용.
--
-- [시드 — 로컬 개발 전용, 공용 RDS에는 적용하지 않는다]
--   post_categories 4종 + 로컬 회원 3명 + 게시글 49건(ACTIVE 47 / BLOCKED 1 / DELETED 1) + 댓글
--   like_count 는 목록 화면 확인용으로 컬럼에 직접 시드한다 (post_likes 정합은 좋아요 기능 구현 시 처리).

SET NAMES utf8mb4;

-- =========================================================
-- 1. 상태 컬럼 정리 (재실행 대비: 기존 CHECK 제거 후 재생성)
-- =========================================================

UPDATE `posts`    SET `status` = 'ACTIVE' WHERE `status` = 'PUBLISHED';
UPDATE `comments` SET `status` = 'ACTIVE' WHERE `status` = 'PUBLISHED';

ALTER TABLE `posts`
    DROP CONSTRAINT IF EXISTS `chk_posts_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT `chk_posts_status`
        CHECK (`status` IN ('ACTIVE', 'DELETED', 'BLOCKED'));

ALTER TABLE `comments`
    DROP CONSTRAINT IF EXISTS `chk_comments_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT `chk_comments_status`
        CHECK (`status` IN ('ACTIVE', 'DELETED'));

ALTER TABLE `post_reports`
    DROP CONSTRAINT IF EXISTS `chk_post_reports_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT `chk_post_reports_status`
        CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED'));

ALTER TABLE `reviews`
    DROP CONSTRAINT IF EXISTS `chk_reviews_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('VISIBLE', 'HIDDEN'));

-- =========================================================
-- 2. 시드 초기화 (커뮤니티 영역만, 재실행 대비)
-- =========================================================

DELETE FROM `post_reports`;
DELETE FROM `post_images`;
DELETE FROM `post_likes`;
DELETE FROM `comments`;
DELETE FROM `posts`;
DELETE FROM `post_categories`;

-- =========================================================
-- 3. 카테고리 (code = enum 이름 저장, 한글은 name 컬럼 = 화면 라벨)
-- =========================================================

INSERT INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`) VALUES
    ('REVIEW',   '후기',   1, 1),
    ('QUESTION', '질문',   1, 2),
    ('RECIPE',   '레시피', 1, 3),
    ('FREE',     '자유',   1, 4);

-- =========================================================
-- 4. 로컬 전용 회원 3명 (닉네임 표시 확인용, 비밀번호 Admin1234!)
-- =========================================================

INSERT IGNORE INTO `members` (`email`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
    ('dessert@cakeshop.local',
     '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
     '단골손님', '010-0000-0003', 'USER', 'ACTIVE'),
    ('baking@cakeshop.local',
     '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
     '홈베이킹러', '010-0000-0004', 'USER', 'ACTIVE'),
    ('cakelover@cakeshop.local',
     '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
     '케이크덕후', '010-0000-0005', 'USER', 'ACTIVE');

-- =========================================================
-- 5. 게시글 시드
-- =========================================================

-- 5-1. 대표 게시글 7건 (목업 화면 내용 재현)
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `view_count`, `like_count`, `status`, `created_at`)
VALUES
    ((SELECT `id` FROM `members` WHERE `email` = 'dessert@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'REVIEW'),
     '딸기 생크림 케이크 재구매 후기입니다',
     '지난달에 이어 두 번째 주문인데 이번에도 만족스러웠어요. 시트가 촉촉하고 생크림이 무겁지 않아요.',
     120, 24, 'ACTIVE', NOW() - INTERVAL 3 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'user@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'QUESTION'),
     '레터링 케이크 문구는 몇 자까지 가능한가요?',
     '주문 제작으로 레터링을 넣고 싶은데 글자 수 제한이 있는지 궁금합니다.',
     45, 5, 'ACTIVE', NOW() - INTERVAL 4 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'baking@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'RECIPE'),
     '집에서 따라 하는 기본 시트 만들기',
     '오븐 예열부터 반죽 순서까지 기본 제누와즈 시트 만드는 과정을 정리했습니다.',
     310, 41, 'ACTIVE', NOW() - INTERVAL 5 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'cakelover@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'FREE'),
     '오늘 픽업한 당일 케이크 너무 예뻐요',
     '픽업하러 갔다가 진열장 보고 한참 구경했네요. 다음엔 초코 케이크 도전!',
     88, 18, 'ACTIVE', NOW() - INTERVAL 5 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'dessert@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'REVIEW'),
     '아이 생일 주문 제작 케이크 후기 (사진 있어요)',
     '캐릭터 도안 그대로 만들어주셔서 아이가 정말 좋아했어요. 맛도 달지 않고 좋았습니다.',
     205, 33, 'ACTIVE', NOW() - INTERVAL 6 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'user@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'QUESTION'),
     '픽업 시간 변경도 가능한가요?',
     '주문할 때 정한 픽업 시간을 하루 전에 바꿀 수 있는지 알고 싶어요.',
     23, 1, 'ACTIVE', NOW() - INTERVAL 7 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'cakelover@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'FREE'),
     '매장 근처 주차 꿀팁 공유합니다',
     '건물 뒤편 공영주차장이 30분 무료예요. 픽업 잠깐이면 충분합니다.',
     67, 9, 'ACTIVE', NOW() - INTERVAL 8 DAY);

-- 5-2. 페이징 확인용 대량 생성 40건 (카테고리·작성자 순환)
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `view_count`, `like_count`, `status`, `created_at`)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 40
)
SELECT
    (SELECT `id` FROM `members`
      WHERE `email` = ELT(1 + (n MOD 5),
        'admin@cakeshop.local', 'user@cakeshop.local', 'dessert@cakeshop.local',
        'baking@cakeshop.local', 'cakelover@cakeshop.local')),
    (SELECT `id` FROM `post_categories`
      WHERE `code` = ELT(1 + (n MOD 4), 'REVIEW', 'QUESTION', 'RECIPE', 'FREE')),
    CONCAT(ELT(1 + (n MOD 4),
        '케이크 후기 남깁니다 #', '궁금한 점 질문드려요 #', '베이킹 팁 공유 #', '자유 수다 #'), n),
    CONCAT('페이징 동작 확인용 샘플 게시글 본문입니다. (', n, '번)'),
    n * 3,
    n MOD 13,
    'ACTIVE',
    NOW() - INTERVAL (8 * 24 + n * 5) HOUR
FROM seq;

-- 5-3. 상태 필터 검증용: 목록에 보이면 안 되는 글 2건
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `view_count`, `like_count`, `status`, `blocked_at`, `blocked_reason`, `blocked_by`, `created_at`)
VALUES
    ((SELECT `id` FROM `members` WHERE `email` = 'user@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'FREE'),
     '[제재됨] 광고성 게시글', '외부 판매 링크 홍보 내용',
     10, 0, 'BLOCKED', NOW() - INTERVAL 1 DAY, '광고·홍보 게시글',
     (SELECT `id` FROM `members` WHERE `email` = 'admin@cakeshop.local'),
     NOW() - INTERVAL 2 DAY),
    ((SELECT `id` FROM `members` WHERE `email` = 'dessert@cakeshop.local'),
     (SELECT `id` FROM `post_categories` WHERE `code` = 'FREE'),
     '[삭제됨] 작성자가 지운 글', '삭제된 본문',
     5, 0, 'DELETED', NULL, NULL, NULL,
     NOW() - INTERVAL 2 DAY);

-- =========================================================
-- 6. 댓글 시드 (댓글 수 표시·DELETED 제외 확인용)
-- =========================================================

INSERT INTO `comments` (`post_id`, `member_id`, `content`, `status`, `created_at`)
VALUES
    ((SELECT `id` FROM `posts` WHERE `title` = '딸기 생크림 케이크 재구매 후기입니다'),
     (SELECT `id` FROM `members` WHERE `email` = 'cakelover@cakeshop.local'),
     '저도 이 케이크 좋아해요. 공감합니다!', 'ACTIVE', NOW() - INTERVAL 2 DAY),
    ((SELECT `id` FROM `posts` WHERE `title` = '딸기 생크림 케이크 재구매 후기입니다'),
     (SELECT `id` FROM `members` WHERE `email` = 'baking@cakeshop.local'),
     '사진도 올려주세요~', 'ACTIVE', NOW() - INTERVAL 2 DAY),
    ((SELECT `id` FROM `posts` WHERE `title` = '딸기 생크림 케이크 재구매 후기입니다'),
     (SELECT `id` FROM `members` WHERE `email` = 'user@cakeshop.local'),
     '(작성자가 삭제한 댓글)', 'DELETED', NOW() - INTERVAL 1 DAY),
    ((SELECT `id` FROM `posts` WHERE `title` = '레터링 케이크 문구는 몇 자까지 가능한가요?'),
     (SELECT `id` FROM `members` WHERE `email` = 'admin@cakeshop.local'),
     '기본 20자까지 가능하고, 초과 시 채팅으로 상담 부탁드립니다.', 'ACTIVE', NOW() - INTERVAL 3 DAY),
    ((SELECT `id` FROM `posts` WHERE `title` = '집에서 따라 하는 기본 시트 만들기'),
     (SELECT `id` FROM `members` WHERE `email` = 'dessert@cakeshop.local'),
     '덕분에 성공했어요. 감사합니다!', 'ACTIVE', NOW() - INTERVAL 4 DAY),
    ((SELECT `id` FROM `posts` WHERE `title` = '집에서 따라 하는 기본 시트 만들기'),
     (SELECT `id` FROM `members` WHERE `email` = 'cakelover@cakeshop.local'),
     '오븐 온도는 몇 도로 하셨나요?', 'ACTIVE', NOW() - INTERVAL 4 DAY);

-- 대댓글 1건 (답글 들여쓰기 표시 확인용) — 같은 테이블 참조라 파생 테이블로 감싼다
INSERT INTO `comments` (`post_id`, `member_id`, `parent_comment_id`, `content`, `status`, `created_at`)
VALUES
    ((SELECT `id` FROM `posts` WHERE `title` = '딸기 생크림 케이크 재구매 후기입니다'),
     (SELECT `id` FROM `members` WHERE `email` = 'dessert@cakeshop.local'),
     (SELECT `id` FROM (SELECT `id` FROM `comments` WHERE `content` = '사진도 올려주세요~') parent),
     '다음 주문 때 꼭 찍어서 올릴게요!', 'ACTIVE', NOW() - INTERVAL 1 DAY);
