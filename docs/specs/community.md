---
domain: community
status: approved
approved-at: 2026-07-25
---

# community 스펙

> **📄 스펙 문서가 뭔가요?** — 코드를 쓰기 **전에** "무엇을, 어떤 규칙으로 만들지" 먼저
> 못 박아 둔 설계 합의서다. 이 문서가 `status: approved`가 되기 전에는 훅이 구현 코드 작성을
> 막는다. 나중에 "왜 이렇게 만들었지?"의 답이 여기 있다.
>
> **이 도메인을 한 줄로** — 회원들이 글과 댓글을 나누는 게시판. 관리자가 부적절한 글을 제재한다.
>
> 모르는 용어가 나오면 [용어 사전](../conventions.md#용어-사전)을 본다.

> 기존 고객·관리자 실구현을 현재 코드와 V2 SQL 기준으로 사후 명문화한 정본이다.
> 범위: 게시글 CRUD, 페이지 번호·무한스크롤 목록, 댓글·답글, 좋아요, 신고 접수, 관리자 제재.

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 회원이 케이크 관련 글과 댓글을 나누고 관리자가 부적절한 콘텐츠를 제재한다.
- 주요 유스케이스:
  1. 방문자 → 게시글 목록·상세 조회 → 페이지 번호 또는 무한스크롤로 탐색
  2. 회원 → 글 작성·수정·삭제, 댓글·1단계 답글 작성·삭제, 좋아요 토글
  3. 회원 → 다른 회원의 정상 게시글 신고 → 중복 없이 PENDING 신고 생성
  4. 관리자 → 전체 상태 목록·상세 조회 → ACTIVE 게시글 제재 또는 BLOCKED 게시글 해제

## 2. 상태값

| 컬럼 | 값 | 시작 상태 | 최종 상태 | 전이 |
|---|---|---|---|---|
| `posts.status` | `ACTIVE / DELETED / BLOCKED` | `ACTIVE` | `DELETED` | 작성자 `ACTIVE → DELETED`, 관리자 `ACTIVE ↔ BLOCKED` |
| `comments.status` | `ACTIVE / DELETED` | `ACTIVE` | `DELETED` | 작성자·관리자 `ACTIVE → DELETED` |
| `post_reports.status` | `PENDING / ACCEPTED / REJECTED` | `PENDING` | `ACCEPTED / REJECTED` | 이번 범위는 PENDING 접수·조회까지, 처리 전이는 후속 관리자 기능 |

- `posts.category`는 상태가 아니라 `REVIEW / QUESTION / RECIPE / FREE` 종류다.
- 좋아요 여부는 `post_likes` 행, 댓글·좋아요 수는 조회 또는 동기화된 파생값이다.

## 3. DB

- `post_categories`, `posts`, `comments`, `post_likes`, `post_reports`
- 글·댓글 삭제는 행 삭제가 아닌 status 변경이다.
- `(post_id, member_id)` 좋아요와 `(post_id, reporter_id)` 신고는 UNIQUE로 중복을 최종 방어한다.
- 닉네임 표시를 위해 `members`를 JOIN하지 않는다.

## 4. 도메인 간 인터페이스

- 사용:
  - `MemberService.getNicknameMap(Collection<Long>)`
  - `MemberService.searchMemberIdsByNickname(String)`
- 제공:
  - `CommunityService.getPopularPosts(int limit)` → `List<PostSummaryView>` — 공개 글 좋아요 순 상위 N건.
    home 메인의 "커뮤니티 인기 글" 섹션이 사용한다.
- Community Mapper에서 members 테이블을 직접 JOIN하지 않는다.

## 5. 화면

- 고객: `/community`, `/community/scroll`, `/community/{id}`, `/community/new`, `/community/{id}/edit`
- API: `/community/api/posts`, `/community/api/posts/{id}/like`
- 관리자: `/admin/community`, `/admin/community/{id}`
- 목록은 페이지 번호 방식과 id cursor 기반 무한스크롤을 모두 제공한다.

## 6. 비즈니스 규칙

- 수정·삭제는 ACTIVE 게시글의 작성자 본인만 가능하다.
- BLOCKED 글은 고객에게 403, DELETED·미존재 글은 404로 처리한다.
- 답글은 같은 게시글의 ACTIVE 최상위 댓글에만 1단계로 작성할 수 있다.
- 삭제된 최상위 댓글은 살아 있는 답글이 있을 때만 마스킹된 자리로 남긴다.
- 본인 글 신고와 중복 신고는 거부한다.
- 좋아요 행과 `posts.like_count` 증감은 같은 트랜잭션에서 처리한다.
- 관리자의 제재 전이는 `ACTIVE → BLOCKED`, 해제는 `BLOCKED → ACTIVE`만 허용한다.

## 7. 완료 기준

- [x] 고객 게시글·댓글·답글·좋아요·신고와 관리자 제재 흐름 구현
- [x] 상태값·CHECK·초기 시드 반영
- [x] member 공개 Service를 통한 닉네임 조회
- [x] Service 전용 테스트
- [x] 고객·관리자 Controller 전용 테스트
- [ ] 신고 `ACCEPTED / REJECTED` 처리 기능은 후속 범위로 별도 스펙
