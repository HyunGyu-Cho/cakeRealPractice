---
domain: member
status: approved
approved-at: 2026-07-27
---

# member 스펙

> **📄 스펙 문서가 뭔가요?** — 코드를 쓰기 **전에** "무엇을, 어떤 규칙으로 만들지" 먼저
> 못 박아 둔 설계 합의서다. 이 문서가 `status: approved`가 되기 전에는 훅이 구현 코드 작성을
> 막는다. 나중에 "왜 이렇게 만들었지?"의 답이 여기 있다.
>
> **이 도메인을 한 줄로** — 회원 가입·로그인·내 정보 수정·탈퇴, 그리고 정지된 계정의 로그인 차단.
>
> 모르는 용어가 나오면 [용어 사전](../conventions.md#용어-사전)을 본다.

> 확정일: 2026-07-25. 범위: 회원가입·마이페이지·프로필/비밀번호 수정·탈퇴·상태별 로그인 차단.
>
> **2차 개정(2026-07-27, 8장)**: 관리자 회원 관리(`/admin/members`)를 범위에 추가한다.
> 1차에서 "추후 별도 착수"로 미뤄 둔 마지막 목업 화면이다.
> 활동 지표는 타 도메인 공개 계약 3개로 받는다(A안 확정).

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 고객 계정의 생성·조회·수정·탈퇴와 계정 상태에 따른 접근 통제.
- 주요 유스케이스 (액터 → 행동 → 결과):
  1. 방문자 → 회원가입 폼 제출(약관 동의 포함) → ACTIVE 회원 생성, 로그인 화면으로 이동
  2. 회원 → 마이페이지 조회 → 본인 회원 정보 표시(주문·쿠폰 블록은 각 도메인 구현 시 연결)
  3. 회원 → 프로필(닉네임·연락처) / 비밀번호 수정 → 저장 후 성공 메시지
  4. 회원 → 탈퇴(확인 다이얼로그) → status=WITHDRAWN + withdrawn_at 기록(soft delete), 세션 종료
  5. 정지·탈퇴 회원 → 로그인 시도 → 거부

## 2. 상태값 (conventions.md 상태값 공통 규칙 준수)

| 컬럼 | 값(영문 enum 이름) | 시작 상태 | 최종 상태 | 전이 규칙 요약 |
|---|---|---|---|---|
| `members.status` | `ACTIVE / SUSPENDED / WITHDRAWN` | `ACTIVE` (DEFAULT) | `WITHDRAWN` | `ACTIVE ↔ SUSPENDED`(관리자, 추후 admin 기능), `ACTIVE → WITHDRAWN`(본인) |

- 로그인 판정: `SUSPENDED` → LockedException(정지 안내), `WITHDRAWN` → UsernameNotFoundException(탈퇴 여부 비노출).
- status가 아닌 것 점검: 통과 — 역할은 `role` 별도 컬럼(USER/ADMIN), 파생값 없음.

## 3. DB

- 사용할 테이블: `members` (V1부터 존재, 이메일 UNIQUE).
- 스키마 변경: `V5_members_status.sql` — `status VARCHAR(20)` + `chk_members_status` CHECK 추가 (V0/V1 소급 반영).
- 탈퇴는 행 삭제가 아닌 soft delete — FK로 참조하는 15개 테이블(orders, posts 등)의 이력 보존.

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드 (기존 유지 + 추가):
  - `getNicknameMap(Collection<Long>)` — 기존, community 사용 중
  - `searchMemberIdsByNickname(String)` — 기존, community 관리자 검색 사용 중
  - `getProfile(Long memberId)` → `MemberProfileView` — 신규 (마이페이지·추후 주문서 주문자 정보)
- 내가 사용할 다른 도메인의 공개 Service 메서드: 없음.

## 5. 화면

- 대상 화면과 URL (URL·템플릿 유지):
  - `GET/POST /signup` → `customer/member/signup`
  - `GET /mypage` → `customer/member/mypage` (회원 정보 블록만 실데이터, 로그아웃은 POST /logout 폼)
  - `GET /mypage/profile`, `POST /mypage/profile`·`/mypage/password`·`/mypage/withdraw` → `customer/member/profile-edit` (폼 3개 분리)
- 목업 JS 임시 동작 중 규칙으로 확정할 것:
  - `data-mock-form` 제출 가로채기 → 실제 POST + PRG로 교체
  - 이메일 "중복 확인" 버튼(핸들러 없음) → 제거, 제출 시 email 필드 에러로 통일
  - 비밀번호 강도 표시(하드코딩) → 제거, 서버 검증(@Size 8자 이상)만
  - 탈퇴 `data-confirm` 확인 다이얼로그 → 유지
- 이 3개 템플릿은 import 스크립트 덮어쓰기 목록에서 제외(community와 동일한 예외).

## 6. 비즈니스 규칙 확정

- 이메일: UNIQUE, 가입 시 중복이면 email 필드 에러. 소문자 정규화 없이 입력값 trim만(로그인과 동일).
- 비밀번호: BCrypt 저장, 8자 이상. 변경 시 현재 비밀번호 일치 확인(불일치 → currentPassword 필드 에러).
- 닉네임: 중복 허용(DDL에 UNIQUE 없음 — 화면 표시는 getNicknameMap 경유라 충돌 무해).
- 약관: 서비스·개인정보 필수(@AssertTrue), 마케팅 선택. 동의 이력 테이블은 만들지 않음(1차 범위 아님).
- team-plan 3장 "정지/탈퇴 로그인 차단 로직 채택 여부" → **채택 확정** (findByEmail에 status 포함 + MemberDetailsService 판정).

## 7. 완료 기준 (2026-07-25 전 항목 충족 — E2E 8단계 검증 완료)

- [x] 가입 → 로그인 → 조회 → 수정 → 탈퇴 → 재로그인 차단 전 흐름 동작
- [x] 입력 검증(form DTO)과 접근 권한(마이페이지 로그인 필수) 적용
- [x] 상태 전이·트랜잭션 규칙 준수
- [x] 전용 테스트 통과 (Service + Controller + MemberDetailsService 확장)
- [x] 관련 SQL(V5 + V0/V1)·문서(conventions·team-plan·README·TodoList) 함께 수정

---

## 8. 2차 개정 — 관리자 회원 관리 (2026-07-27)

### 8.1 범위와 유스케이스

`/admin/members`는 저장소에 마지막으로 남은 목업 화면이다. 실구현 범위는 다음 4개다.

1. 관리자 → 회원 목록 조회(검색·상태 필터·페이징) → 가입일 최신순 목록
2. 관리자 → 회원 상세 조회 → 계정 정보 + 활동 요약(주문·후기·글) + 제재 사유
3. 관리자 → 이용 제한 (사유 입력 필수) → `ACTIVE → SUSPENDED`, 다음 로그인부터 차단
4. 관리자 → 제한 해제 → `SUSPENDED → ACTIVE`, 제재 사유·시각 초기화

**범위 밖(하지 않는 것)**: 관리자의 회원 정보 직접 수정(닉네임·연락처·비밀번호), 관리자에 의한 강제 탈퇴,
제재 이력 테이블(현재 상태와 최근 사유만 컬럼으로 보관), 제재 알림 발송, 이미 로그인한 세션의 즉시 강제 만료.

### 8.2 상태값 — `MemberStatus` 3개를 변경하지 않는다

| 전이 | 주체 | 조건 |
|---|---|---|
| `ACTIVE → SUSPENDED` | 관리자 | 사유 필수(1~500자) |
| `SUSPENDED → ACTIVE` | 관리자 | 사유·시각 초기화(NULL) |
| `ACTIVE → WITHDRAWN` | 본인 | 1차에서 확정 |

- `WITHDRAWN`은 최종 상태다. 관리자도 제재·복구 대상으로 삼지 못한다(`MEMBER_xxx` 에러).
- **관리자 계정(`role = ADMIN`)은 제재할 수 없다.** 자기 자신을 잠그거나 관리자끼리 잠그는 사고를 막는다.
- 상태 전이 검증은 `MemberAdminService`가 소유한다(컨트롤러·화면에 조건문을 두지 않는다).

### 8.3 DB — 스키마 변경 없음

- `members.status`(V5)와 `suspended_at`·`suspended_reason`(V0/V1)이 이미 있고 엔티티에도 매핑돼 있다.
  1차에서 컬럼만 만들어 두고 쓰지 않던 두 컬럼을 이번에 실제로 사용한다.
- `suspended_at`은 업무 시각이라 SQL에서 `CURRENT_TIMESTAMP(6)`로 세팅한다(`withdraw`와 같은 예외 규칙).
- 목록 검색 부하는 회원 규모상 인덱스 추가 없이 감당한다 — **적용할 V파일이 없다.**

### 8.4 도메인 간 인터페이스

목록·상세의 "활동" 지표는 남의 테이블을 JOIN하지 않고 각 도메인 공개 계약으로 받는다(statistics와 같은 방식).

- 신규로 받을 계약:
  - `OrderService.getOrderCountMap(Collection<Long> memberIds)` → `Map<Long, Long>`
    — 회원별 주문 건수. **`CANCELED`·`REJECTED` 제외**(statistics 상품별 집계 기준과 동일).
  - `ReviewService.getReviewCountMap(Collection<Long> memberIds)` → `Map<Long, Long>` — 상태 무관 작성 수.
  - `CommunityService.getPostCountMap(Collection<Long> memberIds)` → `Map<Long, Long>` — `ACTIVE` 글만.
- 목록은 한 페이지(10건)의 id를 모아 **배치 3회**로 조회한다(N+1 금지).
- 내가 새로 제공하는 계약은 없다. 관리자 기능은 member 내부에서 끝난다.

### 8.5 화면

- `GET /admin/members` → `admin/member/list` (URL·템플릿 유지)
  - 검색: 이름(=닉네임) 부분 일치, 이메일 부분 일치, 상태 필터(전체/`ACTIVE`/`SUSPENDED`/`WITHDRAWN`).
  - 목업의 `<select>` 옵션 "정상/이용 제한"은 한글 라벨이므로 `value`에 enum 이름을 넣고 라벨만 한글로 매핑한다.
  - 정렬 가입일 최신순 고정, 페이지 10건, 공통 `pagination` 프래그먼트.
  - 목업의 하드코딩 3행 → `th:each`. "주문 횟수" 열은 8.4의 배치 조회 결과.
- `GET /admin/members/{id}` → `admin/member/detail` (**신규 템플릿**)
  - 목업의 "상세" 버튼은 링크가 없는 죽은 버튼이었다. 이동할 화면이 없으므로 새로 만든다.
  - 계정 정보(이메일·닉네임·연락처·역할·상태·가입일·탈퇴일), 활동 요약 3종, 제재 사유·시각, 제재/해제 폼.
- 제재·해제는 상세 화면의 POST 폼이며 PRG로 상세로 돌아온다. 목록의 버튼도 상세로 보낸다
  (사유 입력이 필수라 목록에서 즉시 제재하지 않는다).
- `mock-notice` 프래그먼트(관리자·고객 양쪽)를 제거한다 — 이 화면이 마지막 사용처였다.

### 8.6 비즈니스 규칙 확정

- 제재 사유는 필수(1~500자). 해제 시에는 사유를 받지 않고 기존 사유를 지운다.
- 제재된 회원은 다음 로그인부터 차단된다(1차에서 이미 `MemberDetailsService`가 `LockedException` 처리).
  **이미 열려 있는 세션은 만료시키지 않는다** — 세션 레지스트리 도입은 별도 사이클로 미룬다(범위 밖).
- 검색은 공백 trim 후 빈 값이면 조건에서 제외한다. 상태 미선택은 전체 조회다.
- 탈퇴 회원도 목록에 남는다(soft delete 이력 보존). 상태 배지로 구분하고 제재 버튼은 노출하지 않는다.

### 8.7 에러 코드 (기존 `MemberErrorCode`에 추가)

| 코드 | 상황 |
|---|---|
| `MEMBER_0xx` 회원 없음 | 기존 코드 재사용 |
| 신규: 관리자 계정 제재 불가 | `role = ADMIN` 대상 제재 시도 |
| 신규: 탈퇴 회원 상태 변경 불가 | `WITHDRAWN` 대상 제재·해제 시도 |
| 신규: 이미 해당 상태 | `ACTIVE` 해제, `SUSPENDED` 재제재 |

### 8.8 완료 기준

- [x] 목록 검색·상태 필터·페이징 동작, 활동 지표 배치 조회(N+1 없음)
- [x] 상세 조회 + 제재(사유 필수) + 해제 동작, PRG·FlashMessage
- [x] 관리자 계정·탈퇴 회원 제재 차단, 중복 상태 전이 차단
- [x] `MemberAdminServiceTests`·`MemberAdminControllerTests`·`MemberAdminScreenRenderingTests`·`MemberAdminMapperTests` 통과.
  목업이 하나도 남지 않아 `AdminPageControllerTests` 자체를 삭제했다.
- [x] README 관리자 표 "목업" → 실구현, TodoList 항목 `[x]`, 스키마 변경 없음 명시
