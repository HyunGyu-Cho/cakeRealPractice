---
domain: member
status: approved
approved-at: 2026-07-25
---

# member 스펙

> 확정일: 2026-07-25. 범위: 회원가입·마이페이지·프로필/비밀번호 수정·탈퇴·상태별 로그인 차단.
> 관리자 회원 관리(/admin/members)는 이번 범위에서 제외(목업 유지, 추후 별도 착수).

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
