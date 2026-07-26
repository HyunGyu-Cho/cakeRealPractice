# 전체 개발 TODO

> 기능을 하나씩 완성하는 순서 정본. 순서는 의존 관계 기준(앞이 없으면 뒤가 안 됨)이며,
> 각 항목은 `/new-domain` 절차(스펙 확정 → DB → 구현 → 테스트) 한 사이클로 진행한다.
> 항목을 시작할 때 `[ ]` → `[~]`(진행 중), 완료 시 `[x]`로 갱신하고 README 현황표도 함께 갱신한다.

## 완료

- [x] **store** — 매장 관리 (조회·수정·휴무일)
- [x] **community** — 고객 목록·상세·글쓰기(페이징 2방식) + 관리자 제재
- [x] **member** — 회원가입·마이페이지·프로필/비밀번호 수정·탈퇴(soft delete)·상태별 로그인 차단 (스펙 docs/specs/member.md, V5)
- [x] **product** — 고객 목록(필터·정렬·검색·페이징)·상세 + 관리자 CRUD·대표 이미지 + 공개 계약 getSalesInfo + 홈 연결 (스펙 docs/specs/product.md, V6)

## 1단계 — 기반 (모든 도메인의 전제)

(완료 — 2단계로 진행)

## 2단계 — 일반 구매 흐름 (핵심 가치 — 여기까지가 기본 MVP)

- [x] **cart** — localStorage 목업 → DB(`carts`/`cart_items`) 전환
  - 스펙 단계에서 team-plan 8장 🟡 임시 답 확정: 같은 상품 수량 합산, 가격 재검증 방식.
- [x] **order(일반) + payment(모의)** — 픽업 설정 → 주문서 → 모의 결제 → `PAID` 생성 → 취소·전액 환불 → 관리자 픽업 처리
  - 결제 전 주문 미생성, UUID 멱등성, 조건부 재고 차감·정확한 복구, 고객 취소 기한과 관리자 취소 규칙 구현.
  - 실제 토스 승인 API·웹훅·외부 결제 보상 처리는 후속 단계.

## 3단계 — 상담·주문제작 흐름 (다음 핵심 마일스톤)

- [x] **chat** — 고객·관리자 영구 1:1 채팅, 텍스트/JPG·PNG 이미지, 읽음·상담 종료/자동 재개, STOMP 실시간 이벤트
  - `ChatRoomStatus`: `OPEN`/`CLOSED`, 메시지 타입: `TEXT`/`IMAGE`/`SYSTEM_CARD`.
  - 정확한 `/주문제작` 명령은 `/orders/custom/options`로 연결되는 시스템 카드로 저장한다.
  - 주문제작 요청·견적·결제 링크 연결은 `order(수제) + payment link` 단계에서 채팅 시스템 카드와 알림으로 확장한다.
- [x] **notification** — 채팅·주문·결제 이벤트 알림 생성 + 읽음 처리(`is_read BOOLEAN`) + STOMP 실시간 푸시
  - `NotificationType` 12개 확정(스펙 `docs/specs/notification.md`). 고객 9개 + 관리자 3개이며, 견적 도착·결제 요청은 값만 정의하고 발행은 주문제작 단계에서 연결한다.
  - 발행 지점: 채팅(발신자 반대편), 결제 완료, 주문 상태 전이, 취소·환불 완료. 업무 트랜잭션에서 저장하고 커밋 후 푸시한다.
  - 전달 이력·재시도 완료(이슈 #10, V12): `notification_deliveries`에 전달 결과를 남기고 실패분은 스케줄러가 최대 3회(백오프 1·2·4분) 재시도한다. `notify()`의 회원 조회는 커밋 후 `NotificationPusher`로 옮겼고, enum ↔ DDL CHECK 동기화 테스트를 뒀다.
  - 전달 경로 통일 완료(이슈 #14): 관리자 공용 토픽을 폐기하고 고객·관리자 모두 `/user/queue/notifications` 개인 큐로 받는다. 관리자 알림도 전달 이력·재시도 대상이다.
- [x] **order(수제) + payment link** — 요청서 제출 → 관리자 검토·견적/반려 → 사용자 견적 수락 → 결제 링크 발급 → 결제 완료 → 제작 시작
  - **구현 완료** (스펙 `docs/specs/order-custom.md`, V13). 상품 옵션 공개 계약 `ProductService.getOptionGroups`와
    채팅 시스템 카드 계약 `ChatService.postSystemCard`를 함께 추가했다.
  - 픽업 규칙 3건을 함께 정비했다(store 공통 코드 — PR에 명시): 유형별 예약 창(일반 14일 /
    주문제작 90일), 슬롯 정원 1건, 픽업 예정 주문이 있는 날짜의 휴무일 지정 차단.
    예약 현황은 store가 정의한 `PickupReservationPort`를 order가 구현해 넘긴다(순환 방지).
  - 요청서: 옵션, 요구사항, 참고 이미지(최대 3장), 희망 픽업 일시, 희망 예산. 최종 가격은 관리자가 견적으로 제시한다.
  - 상태: **`OrderStatus` 7개는 변경하지 않는다.** 요청서 제출 → `UNDER_REVIEW`, 결제 완료 → `IN_PRODUCTION`.
    견적 진행(`SENT`/`ACCEPTED`/`SUPERSEDED`)은 `custom_order_quotes`가, 링크 상태는
    `custom_order_payment_links`가 소유한다. 화면 라벨은 둘을 조합한 파생값이다.
  - 요청서 전용 테이블 없음 — ERD 설계대로 `orders` + `order_items` + `order_item_options` + `order_item_images`.
    뒤 두 테이블은 V0에만 있고 생성된 적이 없어 V13에서 실제로 만들었다.
  - 재견적은 회차(`version`) 행을 누적하고 이전 행을 `SUPERSEDED`로 내리며 그 링크를 `REVOKED`로 무효화한다.
  - 결제 링크: 일회성 토큰, 72시간·제작 가능일 전날 중 이른 쪽 만료, 중복 결제 3중 방어. 채팅 카드 + 알림으로 전달.
  - 결제는 일반 주문과 같은 **모의 결제**다. 실제 토스 승인 API·웹훅은 일반·수제를 함께 전환하는 후속 사이클.
  - ⚠️ `WebhookEventMapper`(인터페이스 + 빈 XML)는 이미 있으나 대응 테이블이 `docs/sql`에 없다. 웹훅 구현 착수 시 **스키마 V파일 추가가 선행**돼야 한다(V13까지 사용 중이므로 그다음 번호를 쓴다).

## 4단계 — 구매 확장·구매 후 경험

- [x] **coupon** — 관리자 발급 + 회원 쿠폰함 + checkout 적용·복구 (스펙 `docs/specs/coupon.md`, V15)
  - 확정: `coupons.status` = `ACTIVE/SUSPENDED/ENDED`, `member_coupons.status` = `AVAILABLE/USED`.
    만료·소진은 저장하지 않는 파생값이다.
  - 확정: 사용은 결제 트랜잭션 안(조건부 UPDATE 1회), 복구는 주문 취소 트랜잭션 안. 초안 단계에서는 잡아두지 않는다.
  - 적용 범위는 일반 주문과 주문제작 **견적 결제 링크** 둘 다. 견적 전 요청서 단계는 확정 금액이 없어 제외한다.
- [x] **review** — 픽업 완료 주문 기준 작성·수정·삭제, 관리자 숨김·답글 (스펙 `docs/specs/review.md`, V16)
  - 확정: 본인 `PICKED_UP` 주문의 **주문 항목당 1개**. 삭제는 하드 삭제라 같은 항목에 다시 쓸 수 있다.
  - 확정: `reviews.status` = `VISIBLE/HIDDEN`. 종합 평점만 필수이고 세부 3축은 선택(NULL 허용)이다.
  - 상품 집계(`average_rating`·`review_count`)는 작성·수정·삭제·숨김·복구마다 재계산해 반영한다.
  - 알림은 범위 밖 — `NotificationType` 12개를 건드리지 않았다. 후기 답글 알림은 후속 사이클.
- [x] **home 연결** — 각 도메인 완성 시 공개 View를 메인에 연결 (별도 단계가 아니라 각 항목의 마지막 체크)
  - 메인(`/`)은 store·product·coupon·review·community의 공개 View만 조합한다(전용 Mapper 없음):
    카테고리 카드(`getCategorySummaries`), 인기 상품(`getPopularActiveProducts`),
    받을 수 있는 쿠폰(로그인 회원만), 베스트 후기(`getLatestVisibleReviews`), 인기 글(`getPopularPosts`).
  - 히어로 배너는 하드코딩으로 남겨 뒀다. statistics 완료 후 전체 통합 점검에서 재검토한다.

## 5단계 — 마감

- [x] **statistics** — 관리자 대시보드(오늘 현황) + 통계(기간 집계) (스펙 `docs/specs/statistics.md`, 스키마 변경 없음)
  - 확정: statistics는 `home`처럼 **얇은 조합 계층**이다. 자기 테이블도 Mapper도 없고(빈 `StatisticsMapper`·XML 삭제),
    집계 SQL은 각 도메인이 자기 테이블에서 돌려 공개 Service 계약으로 노출한다.
  - 확정: **매출의 정본은 payment**다 — 순매출 = 승인 결제(`approved_at`) − 완료된 취소(`canceled_at`).
    order는 `orders.final_amount` 기준 "주문 금액"까지만 노출한다(결제 테이블 JOIN 금지). 추이는 두 계열을 라벨로 합친다.
  - 확정: 완료·취소 건수는 상태 스냅샷이 아니라 기간 내 `picked_up_at`·`canceled_at` 발생 기준,
    상품별 집계는 취소·반려 제외, 재고 부족 임계 5개, 신규 회원은 탈퇴자 포함, 평균 평점은 `VISIBLE`만.
  - 집계 단위 `StatsPeriod`(`DAY`/`WEEK`/`MONTH`)는 저장하지 않는 조회 파라미터이며 `global/common/stats`에 있다(⚠️ global 추가).
  - 그래프는 외부 라이브러리 없이 서버 데이터로 그리는 **인라인 SVG**다(꺾은선 = 주문·순매출, 막대 = 시간대별 픽업).
    좌표 정규화는 `StatisticsService`가 끝내고 템플릿에는 계산식을 두지 않는다.
- [x] **member(관리자)** — 관리자 회원 관리 실구현 (스펙 `docs/specs/member.md` 8장, **스키마 변경 없음**)
  - 저장소의 **마지막 목업 화면**이었다. 이로써 고객·관리자 화면이 전부 실구현이 됐고
    `AdminPageControllerTests`·`fragments/*/mock-notice`를 삭제했다.
  - 확정: `MemberStatus` 3개를 바꾸지 않고 관리자 전이는 `ACTIVE ↔ SUSPENDED`만. 관리자 계정(`role=ADMIN`)
    제재 금지, 탈퇴 회원 상태 변경 금지, 중복 전이 차단. 전이 검증은 `MemberAdminService`가 소유한다.
  - `suspended_at`·`suspended_reason`은 V0/V1에 이미 있고 그동안 안 쓰이던 컬럼이라 적용할 V파일이 없다.
  - 활동 지표는 타 도메인 공개 계약 3개로 받는다(테이블 JOIN 금지): `OrderService.getOrderCountMap`(취소·반려 제외),
    `ReviewService.getReviewCountMap`, `CommunityService.getPostCountMap`. 한 페이지 id를 모아 배치 조회한다.
    공통 운반 타입 `MemberCountRow`는 `global/common/stats`에 뒀다(⚠️ global 추가 — `StatsPeriod`와 같은 자리).
  - 상세 화면(`/admin/members/{id}`)은 신규다 — 목업의 "상세" 버튼에 이동할 화면이 없었다.
  - 범위 밖: 관리자의 회원 정보 직접 수정, 강제 탈퇴, 제재 이력 테이블, 제재 알림, 로그인된 세션 즉시 만료.

- [ ] **전체 통합 점검** — 시드 정리(로컬 vs RDS), `customer-mockup.js` 의존 0 확인, 전체 테스트, README 현황표 갱신

- [ ] **후속: `admin-mockup.js` 제거** — `/admin/members` 실구현으로 이 스크립트를 쓰는 화면이 0이 됐다.
  확인창(`data-confirm`)을 `app.js`가 넘겨받는 통합 점검 PR과 함께 정리한다(두 PR이 모두 dev에 들어간 뒤).

---

### 원칙

- **기본 MVP = 1·2단계.** 일반 상품을 주문하고 픽업하는 서비스가 성립한 뒤, `chat → notification → order(수제)` 순서로 주문제작 흐름을 완성한다.
- **주문제작은 coupon·review보다 먼저 구현한다.** 쿠폰은 견적형 주문의 적용 정책이 필요하고, 리뷰는 완료 주문에 의존하므로 주문제작 흐름이 안정된 뒤 연결한다.
- **미결 규칙은 해당 도메인 차례에 청산한다.** team-plan 8장 빈 칸과 conventions 인벤토리 ☐를 각 항목의 스펙 단계에서 확정한다.
