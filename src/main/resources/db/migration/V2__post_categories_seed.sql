-- V2: 게시판 카테고리 (필수 시드)
--
-- [왜 마이그레이션인가]
--   post_categories는 개발용 더미가 아니라 커뮤니티 글 작성의 전제가 되는 조회용 코드값이다.
--   비어 있으면 어느 환경에서든 글쓰기가 동작하지 않으므로 운영에도 필요하다.
--   화면 확인용 샘플(상품 등)은 db/seed/R__dev_seed.sql로 분리했다.
--
-- [기존 DB 호환]
--   전환 이전 DB는 구 V2_community_status_and_seed.sql로 이미 이 행들을 갖고 있다.
--   베이스라인(V1) 이후로 취급되어 이 파일이 실행되므로 INSERT IGNORE로 멱등하게 둔다.
--
-- [불변]
--   커밋 후 수정하지 않는다. 코드값 변경은 새 V번호 파일로 추가한다.

SET NAMES utf8mb4;

INSERT IGNORE INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`) VALUES
    ('REVIEW',   '후기',   1, 1),
    ('QUESTION', '질문',   1, 2),
    ('RECIPE',   '레시피', 1, 3),
    ('FREE',     '자유',   1, 4);
