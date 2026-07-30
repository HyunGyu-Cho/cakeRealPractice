-- V4: 커뮤니티 성능 인덱스 (부하 측정 기반)
--
-- [배경]
--   게시글 100,000건 · 댓글 300,000건 · 동시 16커넥션 부하 측정 결과:
--   - 메인 페이지 인기 글(findPopularPosts) p50 11.1초 — like_count 정렬이 풀스캔+filesort
--   - 커뮤니티 목록(findActivePostPage) p50 19.0초, 무한스크롤 p50 7.3초
--   - EXPLAIN: posts type=ALL + Using temporary; Using filesort,
--     댓글수 DEPENDENT SUBQUERY가 LIMIT 이전 전체 행에 대해 실행됨.
--   posts·comments에는 PK·UNIQUE·FK 인덱스뿐이라 조회 패턴을 받쳐줄 보조 인덱스가 없다.
--   (orders·reviews·coupons가 이미 조회용 복합 인덱스를 가진 것과 같은 원칙을 적용한다.)
--
-- [설계 원칙]
--   복합 인덱스는 등치 조건(status)을 앞에, 범위·정렬 컬럼(id, like_count)을 뒤에 둔다(좌측 접두 규칙).
--   인덱스는 쓰기 시 B-tree 갱신 비용을 수반하므로 실제 쿼리 패턴이 확인된 조합에만 최소로 건다.

-- 1) 목록·카운트·무한스크롤(keyset): WHERE status + ORDER BY id
CREATE INDEX `idx_posts_status_id` ON `posts` (`status`, `id`);

-- 2) 카테고리 필터 목록·카운트: 커버링으로 카운트가 인덱스만으로 끝난다
CREATE INDEX `idx_posts_category_status_id` ON `posts` (`category_id`, `status`, `id`);

-- 3) 메인 인기 글: WHERE status + ORDER BY like_count DESC, id DESC (역방향 인덱스 스캔)
CREATE INDEX `idx_posts_status_like_count_id` ON `posts` (`status`, `like_count`, `id`);

-- 4) 댓글수 서브쿼리 커버링: FK 인덱스(post_id 단일)로는 status 확인에 행 접근이 필요하다.
--    (post_id, status)면 COUNT가 인덱스만으로 끝난다 (EXPLAIN Extra: Using index)
CREATE INDEX `idx_comments_post_status` ON `comments` (`post_id`, `status`);

-- [적용 후 동일 부하 실측]
--   인기 글 p50 11,069ms → 2.8ms · 목록 p50 19,036ms → 170ms (mapper 리라이트 결합)
--   전체 처리량 2.8 → 96.2 ops/s
