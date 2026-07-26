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
  - 히어로 배너는 하드코딩이다. 재검토 결과 아래 "문서·잔재 정리"에서 목업 문구를 걷어내고 CTA를 붙였다.

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
- [x] **전체 통합 점검** — 시드 정리(로컬 vs RDS), `customer-mockup.js` 의존 0 확인, 전체 테스트, README 현황표 갱신
  - 시드 구분 기준 확정(team-plan 8장 ⬜ 청산): 기준은 "없으면 앱이 동작하지 않는가" 하나.
    필수 시드(categories·대표 매장 1행+7요일·post_categories)만 공용 RDS에 적용하고 데모 시드(샘플 계정·
    커뮤니티 샘플 글·상품/주문제작/쿠폰 샘플)는 로컬 전용이다. V파일은 쪼개지 않고 절 단위로 구분한다.
    표는 README "시드 구분 (로컬 vs 공용 RDS)".
  - `customer-mockup.js` 의존 0 달성: 남아 있던 4개 화면(로그인·상품 목록·회원가입·프로필 수정)이 쓰던
    순수 UI 동작(모달·전체 동의·확인창)을 `app.js`로 옮기고 script 태그를 제거했다(⚠️ global 공통 코드 변경 — PR에 명시).
    `app.js`의 죽은 localStorage 장바구니 카운터(`updateMockCartCount`)도 함께 제거했다(참조 0).
    재발 방지로 템플릿 전체를 훑어 참조 0을 검증하는 테스트를 뒀다.
  - 전체 테스트 통과. 목업 번들 2개(`customer-mockup.js`·`admin-mockup.js`)는 **파일로 남기되 의존만 0으로**
    만들었다 — 새 목업을 들여올 때 다시 쓰는 자산이라 지우지 않는다. 두 번들 모두 `[data-confirm]`
    핸들러가 있어 `app.js`와 함께 로드되면 확인창이 두 번 뜨므로, 참조 0을 테스트로 고정했다.
  - `app.js`는 화면마다 붙이지 않고 공통 프래그먼트에서만 로드한다(고객 `common/head`, 관리자 `admin/header`).
    관리자 header에 있던 목업 번들을 `app.js`로 교체하고 개별 화면 8곳의 중복 태그를 걷어냈다.

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
  - `admin-mockup.js`는 이 화면이 마지막 사용처였다. 삭제하지 않고 `customer-mockup.js`와 같은 원칙으로
    **"파일은 남기고 의존 0"** 으로 맞췄다(위 통합 점검 항목과 한 세트).
- [x] **디자인 토큰 재정비 (토스 라이트 블루)** — 전 화면 팔레트·형태감 교체 (규격 `docs/frontend-template-format.md` "디자인 토큰")
  - 브랜드 색이 없던 무채색 팔레트를 토스(TDS) 기준 라이트 블루로 교체했다(⚠️ app.css는 전 도메인 공유 — PR에 명시).
  - 확정: **색 리터럴은 `:root`에만 둔다.** 규칙 안 하드코딩 55곳(app.css 50 + chat.css 5)을 토큰화해
    팔레트 교체가 `:root` 한 줄로 끝나게 만들었다. 검사 명령은 규격 문서에 있다.
  - 확정: **파란 글자는 `--color-info`, 파란 면은 `--color-primary`.** `#3182f6`은 흰 배경 3.71:1로 본문 텍스트에 부적합하다.
    TDS 원본 Red·Green도 텍스트 대비가 모자라 시맨틱 색은 명도대비를 맞춰 조정했다.
  - `customer-mockup.css`가 app.css 뒤에 로드되며 `:root`를 재선언해 고객 화면 11개의 팔레트를 되돌리고 있었다.
    부분집합임을 검증하고 링크를 제거했다(파일은 목업 번들과 같은 원칙으로 존치, 참조 0을 테스트로 고정).
  - 남은 작업이던 채팅 CSS 중복 통합은 아래 항목에서 끝냈다.
- [x] **채팅 스타일 중복 통합 (app.css → chat.css)** — 위 리테마링에서 분리해 뒀던 후속 작업
  - `.chat-*` 를 쓰는 화면은 `chat.css`를 함께 싣는 채팅 2개(고객·관리자)뿐이라, 전 화면에 실리는
    app.css의 채팅 규칙 33줄은 **나중에 로드된 chat.css가 이기는 사문 규칙**이었다. app.css에서 걷어냈다.
  - 확정: **chat.css가 채팅 스타일의 단일 출처.** 값이 어긋나 있던 곳은 chat.css 값을 정본으로 삼았다
    (`.chat-messages` 높이·`.chat-msg` 최대폭·`.chat-layout` 사이드바 폭 등 — 원래 chat.css가 이기던 값이라 화면은 그대로다).
  - 실제로 app.css만 갖고 있던 규칙 3개는 chat.css로 옮겼다 — 말풍선 꼬리(`--me`/`--other` 비대칭 모서리),
    `.chat-msg--me .chat-msg__meta{text-align:right}`, `.chat-msg__read`. 옮기며 `8px` 리터럴은 `--radius-sm`으로 바꿨다.
  - 정리한 사용처 없는 규칙 5개: `.chat-date-divider`(+span), `.chat-msg--other__content-wrap`,
    `.chat-input-area`, `.chat-sidebar`, `.chat-order-item` — 템플릿·JS 어디서도 쓰지 않는다.
  - **화면 변화 0.** app.css의 `.chat-messages{max-height:480px}`가 chat.css의 `height:min(56vh,560px)`를
    깎아 실제 높이는 360~480px였다. app.css를 걷어내면 상한이 560으로 풀리므로, 두 속성이 싸우던 것을
    `height:min(56vh,480px)` 한 곳으로 합쳐 480 상한을 chat.css가 직접 갖게 했다(좁은 화면 규칙도 동일).
    상한을 560으로 올리는 건 별개의 디자인 판단이라 이 리팩터링에 섞지 않았다.
  - 양방향을 테스트로 고정했다(`chatStylesLiveOnlyInChatCss`) — app.css에 `.chat-*` 금지, 그리고
    `.chat-*` 를 쓰는 화면은 chat.css를 반드시 링크. 두 조건 모두 실제로 깨뜨려 실패하는 것을 확인했다.
- [x] **문서·잔재 정리** — 계획된 항목을 전부 끝낸 뒤 남아 있던 문서 지연과 죽은 코드 청산
  - team-plan 8장의 마지막 🟡 3행을 확정으로 갱신했다. 구현은 끝났는데 표만 목업 JS 임시 답에 머물러 있었다 —
    장바구니 합산(픽업 일시는 장바구니에 저장하지 않으므로 목업의 "일시 다르면 별도 항목"은 성립하지 않는다),
    담은 가격 미저장·`getSalesInfo()` 재검증, `order_items` 스냅샷 저장. 8장 서두의 목업 안내문도 걷어냈다.
  - conventions 상태 인벤토리의 community 3행(`posts`·`comments`·`post_reports`)이 "거의 확정"·"☐ 열림"으로
    남아 있던 것을 V2·enum 기준으로 확정 처리하고, "담당자가 채우는 ☐" 표를 확정 결과표로 바꿨다.
    이로써 열린 상태값은 0이다. ⚠️ `post_reports.status`만 자바 enum 없이 문자열로 다루는 것을 인벤토리에 명시했다.
  - 참조 0인 빈 껍데기 2개 삭제: `PostLike`(좋아요는 mapper 파라미터 방식으로 이미 동작 — 엔티티를 쓴 적이 없다),
    `ApprovalView`(전 소스 참조 0). 토스 골격 4개는 6단계 착지점이라 존치한다.
  - 홈 히어로에서 "서비스 소개 배너 영역" 목업 문구를 걷어내고 카피 + CTA 2개(케이크 둘러보기·주문제작 시작하기)로
    바꿨다. `.hero-placeholder` → `.hero` 계열로 교체했고 색은 토큰만 썼다(⚠️ app.css — 전 도메인 공유).

## 6단계 — 후속 (계획된 마일스톤을 모두 끝낸 뒤 진행)

> 5단계까지가 "전 도메인 실구현" 목표선이고, 아래 둘은 그 뒤로 미뤄 둔 항목이다.
> 각각 `/new-domain` 절차로 스펙을 먼저 확정하고 착수한다.

- [ ] **토스 실결제 전환 (테스트 API)** — 모의 결제를 토스 테스트 키 기반 실제 승인·취소·웹훅으로 교체
  - 지금은 일반·수제 모두 **모의 결제**다. 외부 승인 없이 주문 `PAID`와 결제 `DONE`을 같은 트랜잭션에 만든다.
  - 골격만 있고 비어 있는 클래스 4개가 이 작업의 착지점이다 — `TossPaymentClient`(승인·취소·조회),
    `TossWebhookController`(`POST /webhooks/toss`), `WebhookEvent`, `WebhookEventMapper`(+ 빈 XML).
    잔재로 지우지 않고 남겨 뒀다.
  - ⚠️ **스키마 V17이 선행**돼야 한다. `WebhookEventMapper`에 대응하는 웹훅 이벤트 테이블이 `docs/sql`에 없다
    (V16까지 사용 중이므로 V17). 전송 ID UNIQUE로 멱등을 잡고 10초 내 200을 돌려주는 설계가 전제다.
  - 함께 정할 것: 승인 실패·타임아웃 시 보상 처리, 결제 상태 대조(`find`) 배치, 테스트 키·비밀값의 `.env` 항목,
    `PaymentStatus` 6개를 유지한 채 외부 상태를 어떻게 매핑할지.
  - 일반 주문과 주문제작 결제 링크를 **함께** 전환한다. 한쪽만 실결제로 두면 취소·환불 경로가 갈린다.

- [ ] **후기 답글 알림** — 관리자가 후기에 답글을 달면 작성자에게 알림
  - review 스펙(`docs/specs/review.md`)에서 "알림은 범위 밖"으로 명시하고 미뤄 둔 항목이다.
    답글 기능 자체는 이미 구현돼 있고 알림 발행만 없다.
  - `NotificationType`을 12개에서 늘려야 한다 — enum ↔ DDL CHECK 동기화 테스트가 있으므로 **V파일과 함께** 고친다.
  - 발행 지점은 관리자 답글 저장 트랜잭션이고, 전달은 기존 경로(커밋 후 `NotificationPusher` →
    `/user/queue/notifications` 개인 큐 + `notification_deliveries` 재시도)를 그대로 탄다.

---

### 원칙

- **기본 MVP = 1·2단계.** 일반 상품을 주문하고 픽업하는 서비스가 성립한 뒤, `chat → notification → order(수제)` 순서로 주문제작 흐름을 완성한다.
- **주문제작은 coupon·review보다 먼저 구현한다.** 쿠폰은 견적형 주문의 적용 정책이 필요하고, 리뷰는 완료 주문에 의존하므로 주문제작 흐름이 안정된 뒤 연결한다.
- **미결 규칙은 해당 도메인 차례에 청산한다.** team-plan 8장 빈 칸과 conventions 인벤토리 ☐를 각 항목의 스펙 단계에서 확정한다.
