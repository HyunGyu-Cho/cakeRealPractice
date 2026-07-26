---
domain: order-custom
owner: 주환
status: approved
approved-at: 2026-07-26
---

# 주문제작 주문·결제 링크 명세

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 가격이 미리 정해지지 않는 수제 케이크를 요청서 → 관리자 견적 →
  고객 수락 → 결제 링크 순으로 처리해 제작에 착수시킨다.
- 주요 유스케이스 (액터 → 행동 → 결과):
  1. 고객 → `/orders/custom/options`에서 옵션·요구사항·참고 이미지·희망 픽업일시·희망 예산을 채워 제출
     → `UNDER_REVIEW` 주문이 생성되고 전체 관리자에게 신규 요청 알림이 간다.
  2. 관리자 → 요청 검토 후 최종 금액·제작 가능일로 **견적 발송** → 고객에게 `CUSTOM_ORDER_QUOTE` 알림과
     채팅 시스템 카드가 전달된다.
  3. 관리자 → 제작 불가 시 **반려**(사유 필수) → 주문 `REJECTED`, 고객에게 `ORDER_REJECTED` 알림.
  4. 고객 → 견적 수락 → 결제 링크가 발급되고 `PAYMENT_REQUESTED` 알림과 채팅 카드가 전달된다.
  5. 고객 → 결제 링크로 모의 결제 완료 → 주문 `UNDER_REVIEW → IN_PRODUCTION`, `ORDER_PAID` 알림.
  6. 관리자 → 이후 `IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP`은 **일반 주문과 동일한**
     `FulfillmentService` 흐름을 그대로 탄다.

범위 밖: 실제 토스 승인 API·웹훅(`TossPaymentClient`·`TossWebhookController`·`webhook_events`)은
일반 결제도 아직 모의이므로 일반·수제를 함께 전환하는 후속 사이클에서 다룬다. 쿠폰 적용은 coupon 차례.

## 2. 상태값 (conventions.md 상태값 공통 규칙 준수)

**`OrderStatus` 7개는 팀 확정값이며 이번 작업에서 값도 전이 규칙도 바꾸지 않는다.**
견적 진행 상황은 주문 상태가 아니라 견적 테이블이 소유한다.

| 컬럼 | 값(영문 enum 이름) | 시작 상태 | 최종 상태 | 전이 규칙 요약 |
|---|---|---|---|---|
| `orders.status` | `OrderStatus` 7개(기존) | `UNDER_REVIEW` (요청서 제출 시 서비스가 세팅) | `PICKED_UP` / `CANCELED` / `REJECTED` | 기존 `OrderStatus.canTransitionTo()`가 소유. 수제 경로 `UNDER_REVIEW → IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP`, 반려 `UNDER_REVIEW → REJECTED` |
| `custom_order_quotes.status` | `QuoteStatus` — `SENT / ACCEPTED / SUPERSEDED` | `SENT` (DDL DEFAULT) | `ACCEPTED` / `SUPERSEDED` | `SENT → ACCEPTED`(고객 수락), `SENT → SUPERSEDED`(재견적 발행 시 이전 행). `ACCEPTED`·`SUPERSEDED`는 최종. 전이는 `QuoteStatus.canTransitionTo()`가 소유 |
| `custom_order_payment_links.status` | `PaymentLinkStatus` — `ISSUED / USED / EXPIRED / REVOKED` | `ISSUED` (DDL DEFAULT) | `USED` / `EXPIRED` / `REVOKED` | `ISSUED → USED`(결제 성공), `ISSUED → EXPIRED`(만료 확인 시), `ISSUED → REVOKED`(재견적·주문 취소로 무효화). 최종 상태에서 재전이 없음 |
| `product_options.status` | `ProductOptionStatus` — `ACTIVE / INACTIVE` | `ACTIVE` (DDL DEFAULT) | 없음(양방향) | 관리자가 옵션 노출을 껐다 켤 수 있다. product 스펙이 이번 차례로 넘긴 ☐ 항목을 여기서 확정 |

주문 상태는 결제 전까지 `UNDER_REVIEW`를 유지한다. 견적을 여러 번 주고받아도 주문 상태는 움직이지 않는다.

- status가 아닌 것 점검:
  - "견적 발송됨 / 수락 완료 / 결제 대기" 같은 화면 라벨 → **파생값.** `orders.status`와 최신 견적·링크
    상태를 조합해 화면에서만 만든다. 별도 컬럼으로 저장하지 않는다.
  - 요청서 초안(`DRAFT`) → **상태값을 두지 않는다.** 작성 중 내용은 저장하지 않고 제출 시에만 행을 만든다.
  - 견적 회차 → **`version` 정수**, 상태가 아니다.
  - 결제 만료 여부 → `expires_at`과 현재 시각의 **파생값**. `EXPIRED`는 스케줄러·조회 시점에 확정 기록만 한다.
  - `product_option_groups.selection_type`(`SINGLE`/`MULTIPLE`) → **종류**, 상태가 아니다.

## 3. DB

- 사용할 기존 테이블: `orders`, `order_items`, `products`, `product_option_groups`, `product_options`,
  `payments`, `chat_rooms`, `chat_messages`, `notifications`
  - **요청서 전용 테이블을 새로 만들지 않는다.** V0 ERD가 이미 주문제작을 `orders` 계열에 담도록 설계했다.
    요청서 = `orders`(`UNDER_REVIEW`) 1행 + `order_items` 1행 + `order_item_options` N행 + `order_item_images` N행.
    반려 사유는 기존 `orders.reject_reason`/`rejected_at`, 요구사항은 기존 `order_items.requirements`를 쓴다.
- 스키마 변경 필요 여부: **필요** — `docs/sql/V13_custom_order.sql` 신규 (V0_ERD.sql·V1_first_MVC_table.sql 소급 반영)

  1. `order_item_options`, `order_item_images` — **V0 ERD에 설계만 있고 생성된 적 없는 테이블을 실제로 만든다**
     (V12가 `notification_deliveries`에 한 것과 같은 상황). 컬럼은 ERD 원안 그대로.
  2. `custom_order_quotes` 신규
     - `id`, `order_id`(FK orders), `version INT NOT NULL`, `quoted_amount DECIMAL(12,0) NOT NULL`,
       `producible_date DATE NOT NULL`, `admin_note VARCHAR(500) NULL`, `issued_by`(FK members),
       `status VARCHAR(20) NOT NULL DEFAULT 'SENT'`, `sent_at`, `accepted_at DATETIME(6) NULL`,
       `created_at`, `updated_at`
     - `uk_custom_order_quotes_version UNIQUE (order_id, version)` — 재견적 동시 발행 시 회차 충돌을 DB가 막는다
     - `chk_custom_order_quotes_status CHECK (status IN ('SENT','ACCEPTED','SUPERSEDED'))`
     - `idx_custom_order_quotes_order (order_id, version DESC)` — 최신 견적 조회
  3. `custom_order_payment_links` 신규
     - `id`, `quote_id`(FK custom_order_quotes), `token VARCHAR(64) NOT NULL`, `amount DECIMAL(12,0) NOT NULL`,
       `expires_at DATETIME(6) NOT NULL`, `used_at DATETIME(6) NULL`, `revoked_at DATETIME(6) NULL`,
       `status VARCHAR(20) NOT NULL DEFAULT 'ISSUED'`, `created_at`
     - `uk_custom_order_payment_links_token UNIQUE (token)`
     - `uk_custom_order_payment_links_quote UNIQUE (quote_id)` — 견적 1건당 링크 1건. **중복 결제 방지의 1차 방어선**
     - `chk_custom_order_payment_links_status CHECK (status IN ('ISSUED','USED','EXPIRED','REVOKED'))`
  4. `orders`에 `desired_budget DECIMAL(12,0) NULL` 추가 — 고객 희망 예산. 수제 전용이라 NULL 허용.
     일반 주문 흐름은 이 컬럼을 읽지도 쓰지도 않는다.
  5. `product_options`에 `chk_product_options_status CHECK (status IN ('ACTIVE','INACTIVE'))` 추가하고
     타입을 `VARCHAR(30)` → `VARCHAR(20)`으로 좁힌다(conventions 상태 컬럼 규칙).
  6. CUSTOM 상품의 옵션 시드 — 목업 `custom-option.html`의 크기·맛·색상을
     `product_option_groups`/`product_options` 행으로 넣는다. 하드코딩된 목업 값을 DB 정본으로 옮기는 것이다.

- `created_at`/`updated_at`은 전부 DDL DEFAULT에 위임한다. `sent_at`/`accepted_at`/`expires_at`/`used_at`은
  업무 컬럼이라 서비스가 계산해 세팅한다.
- `orders.final_amount`는 제출 시 옵션 합산 **예상 금액**으로 채우고, 견적 수락 시 견적 금액으로 UPDATE한다.
  클라이언트가 보낸 금액은 계산에도 저장에도 쓰지 않는다(order-payment.md와 동일 규칙).

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드 (`CustomOrderService`):
  - `Long submitRequest(Long memberId, CustomOrderForm form)` — 요청서 제출, 생성된 orderId 반환
  - `CustomOrderDetailView getMyRequest(Long memberId, Long orderId)` — 고객 상세
  - `void acceptQuote(Long memberId, Long orderId)` — 견적 수락 + 결제 링크 발급
  - `void cancelRequest(Long memberId, Long orderId)` — 수락 전 고객 취소
  - `CustomOrderAdminService`: `quote(...)`, `reject(...)`, `getRequestPage(...)`, `getRequestDetail(...)`
  - `CustomOrderPaymentService`: `resolveLink(String token)`, `pay(String token, Long memberId)`
- 내가 사용할 다른 도메인의 공개 Service 메서드 (Mapper 직접 호출 금지):
  - `ProductService.getSalesInfo(productId)` — 기존 계약. 상품명·기본가·유형·준비일·취소기한 확인
  - `ProductService.getOptionGroups(productId)` — **신규 공개 계약**(product 도메인에 추가).
    옵션 그룹·옵션 목록 조회. product 스펙이 옵션 관리를 이번 차례로 넘겼으므로 여기서 정의한다.
  - `StoreService`의 영업일·휴무일·영업시간 조회 — 픽업 슬롯 검증(order-payment.md와 동일 규칙 재사용)
  - `NotificationService.notify(...)` / `notifyAdmins(...)` — 알림 발행
  - `ChatService.postSystemCard(...)` — **신규 공개 계약**(chat 도메인에 추가). 견적·결제 링크 카드 발행
  - `PaymentService` — 모의 결제 저장. 일반 결제와 같은 `payments` 행을 만든다
- 호출 방향은 단방향이다. product·store·chat·notification은 주문제작을 역참조하지 않는다.
- `orders`·`order_items`는 order 도메인 자신의 테이블이므로 `OrderMapper` 계열을 직접 쓴다(도메인 내부).

## 5. 화면

- 대상 화면과 URL (목업 → 실구현 전환 시 URL·템플릿 유지):
  - 고객 `GET/POST /orders/custom/options` → `customer/order/custom-option.html` (요청서 작성·제출)
  - 고객 `GET /orders/custom/{orderId}` → `customer/order/custom-request.html` (요청 상세·견적 확인)
    - 목업 URL `/orders/custom/request`는 orderId 없는 진입점이라 **본인 최신 요청으로 리다이렉트**한다
  - 고객 `POST /orders/custom/{orderId}/accept` (견적 수락), `POST /orders/custom/{orderId}/cancel`
  - 고객 `GET /orders/custom/pay/{token}`, `POST /orders/custom/pay/{token}` (결제 링크)
  - 관리자 `GET /admin/custom-orders`, `GET /admin/custom-orders/{orderId}`,
    `POST /admin/custom-orders/{orderId}/quote`, `POST /admin/custom-orders/{orderId}/reject`
    → 목업이 없어 신규 작성. `admin/order/*` 기존 화면과 `StoreAdminController` 패턴을 따른다
- 목업 JS가 시연하는 임시 동작 중 규칙으로 확정할 것:
  - `data-option-price` 기반 클라이언트 금액 계산은 **표시 전용**이다. 서버가 `product_options.additional_price`로
    다시 계산하며 클라이언트 값은 신뢰하지 않는다.
  - `data-add-custom-cart`("장바구니 담기")는 **제거한다.** 주문제작은 견적을 거쳐야 금액이 정해지므로
    장바구니 경로를 지원하지 않는다. `customer-mockup.js`의 `?intent=cart-edit` 분기도 함께 정리한다.
  - 목업의 "거절 시 표시 영역"·"승인 시 표시 영역" 두 점선 패널은 각각 `REJECTED`·견적 `SENT` 상태에서만
    렌더되는 실제 조건부 블록으로 바꾼다.
  - "승인 시 → 결제하기" 버튼의 `/orders/checkout` 링크는 **결제 링크 URL**로 교체한다.
    주문제작은 일반 체크아웃을 타지 않는다.
  - "재요청하기"는 수정 재제출이 아니라 **새 요청서 작성**(`/orders/custom/options`)으로 확정한다.
  - `scripts\import-customer-mockups.ps1`의 `$screenMap`에서 `custom-option.html`·`custom-request.html`을
    **먼저 제외**해야 한다. 제외 전에 스크립트를 돌리면 실구현이 목업으로 덮인다.

## 6. 비즈니스 규칙 확정

- team-plan.md 8장에서 이 도메인과 관련된 항목: "주문제작 요청·견적 처리 방식", "결제 링크 만료 정책" (미결)
- 확정한 규칙 (확정 후 team-plan 8장 표 갱신):
  1. **요청서 초안을 저장하지 않는다.** 제출 시에만 `orders` 행이 생긴다. 참고 이미지도 제출 트랜잭션에서
     저장하므로 고아 파일 정리 배치가 필요 없다. cart의 `CheckoutDraft` 세션 패턴과 같은 방향이다.
  2. **주문 상태와 견적 진행을 분리한다.** 같은 사실을 두 컬럼이 들고 있으면 한쪽만 갱신됐을 때 어긋나므로,
     `orders.status`는 확정 7개만 쓰고 견적 진행은 `custom_order_quotes.status`가 단독으로 소유한다.
  3. **재견적은 고객 수락 전까지만 가능하다.** 새 견적을 발행하면 같은 트랜잭션에서 이전 `SENT` 행을
     `SUPERSEDED`로 내리고, 그 견적의 결제 링크가 있으면 `REVOKED`로 무효화한다. 이미 `ACCEPTED`인
     견적에는 재견적할 수 없다(`CUSTOM_ORDER_00x QUOTE_ALREADY_ACCEPTED`).
  4. **결제 링크는 일회성이다.** 토큰은 `UUID` 기반 64자 이내 난수이며 URL로만 전달한다.
     유효기간은 **발급 후 72시간**이고 견적의 `producible_date` 전날을 넘기지 않는다(둘 중 이른 쪽).
     `ISSUED`가 아니거나 만료된 토큰은 결제를 거부한다. 링크 소유자 검증은 토큰만으로 하지 않고
     **로그인 회원이 주문 소유자인지 반드시 함께 확인한다.**
  5. **중복 결제를 3중으로 막는다.** ① `uk_custom_order_payment_links_quote`로 견적당 링크 1건,
     ② 결제 트랜잭션에서 링크 행을 `SELECT ... FOR UPDATE`로 잠그고 `ISSUED`인지 재확인,
     ③ `payments.idempotency_key`에 토큰을 넣어 UNIQUE 충돌 시 롤백 후 기존 결제의 주문을 반환한다
     (order-payment.md의 UUID 멱등 처리와 같은 방식).
  6. **결제 완료 트랜잭션**은 링크 `USED`, 견적 금액으로 `orders.final_amount` 확정,
     주문 `UNDER_REVIEW → IN_PRODUCTION`, `payments` `DONE` 저장을 한 트랜잭션으로 처리한다.
     주문제작 상품은 `stock_quantity`가 NULL(재고 관리 안 함)이므로 **재고 차감이 없다.**
  7. **취소 규칙.** 고객은 견적 수락 전(`UNDER_REVIEW` + 최신 견적이 `ACCEPTED` 아님)까지 취소할 수 있고,
     이때 발급된 링크는 `REVOKED`가 된다. 결제 후에는 order-payment.md의 일반 취소·환불 규칙을 그대로
     따른다(취소 기한은 `pickup_at - cancellation_limit_days`). 관리자는 `IN_PRODUCTION`까지 취소할 수 있다.
  8. **반려 사유는 필수**이며 `orders.reject_reason`에 저장한다. `REJECTED`는 최종 상태라 되돌릴 수 없고,
     고객은 새 요청서를 쓴다.
  9. **픽업 일시 검증.** order-payment.md의 픽업 규칙(매장 영업일·휴무일·영업시간·슬롯 간격 교집합,
     종료 시각 미포함)을 그대로 재사용한다. 다만 하한은 상품 `preparation_days`가 아니라
     **견적의 `producible_date`**다. 요청서 제출 시점에는 상품 `preparation_days`를 하한으로 쓰고,
     견적 수락 시 `producible_date` 기준으로 다시 잡는다. 희망 픽업일이 제작 가능일보다 이르면
     수락 시점에 **제작 가능일 이후 첫 예약 가능 슬롯으로 스냅**한다(원래 시각대를 우선하고,
     휴무일이면 다음 영업일로 넘긴다). 날짜를 그대로 밀면 휴무일·영업시간 밖에 떨어질 수 있다.
     - ⚠️ **픽업 예약 창은 오늘부터 14일**이다(`StoreService.isDateInPickupWindow`, 일반 주문과 공유).
       주문제작은 제작 기간이 이보다 길 수 있으므로 관리자 견적 단계에서 `producible_date`가 창을
       넘으면 `CUSTOM_020`으로 막는다. 장기 리드타임을 허용하려면 픽업 창을 매장 설정값으로 빼야
       하는데 이는 **store 공통 코드 변경이라 별도 PR 합의**가 필요하다(후속 과제).
  10. **알림·채팅 발행 지점.** 값만 정의돼 있던 두 타입을 여기서 실제로 발행한다.
      | 시점 | 알림 | 채팅 시스템 카드 |
      |---|---|---|
      | 요청서 제출 | `ADMIN_ORDER_PLACED`(전체 관리자) | 없음 |
      | 견적 발송 | `CUSTOM_ORDER_QUOTE`(고객) | 견적 카드 → `/orders/custom/{orderId}` |
      | 반려 | `ORDER_REJECTED`(고객) | 없음 |
      | 결제 링크 발급 | `PAYMENT_REQUESTED`(고객) | 결제 카드 → `/orders/custom/pay/{token}` |
      | 결제 완료 | `ORDER_PAID`(고객) + `ADMIN_ORDER_PLACED`(관리자) | 없음 |
      알림은 notification.md 규칙대로 업무 트랜잭션에서 저장하고 커밋 후 푸시한다.
  11. **채팅방은 참조 컬럼 없이 찾는다.** `chat_rooms`에 `uk_chat_rooms_customer` UNIQUE가 있어
      고객당 방이 1개이므로, 주문의 `member_id`로 방을 조회하면 된다. 요청서에 `chat_room_id`를 두지 않는다.
      방이 아직 없으면 시스템 카드 발행을 건너뛴다(알림은 정상 발행). 카드 발행 실패가 견적 발송을
      롤백시키지 않도록 커밋 후 처리한다.
  12. **참고 이미지는 최대 3장, 장당 5MB, JPG/PNG만** 허용한다. chat의 `ChatImageStorage` 제약과 맞춘다.
      저장은 `FileStorageClient`를 쓰고 경로만 `order_item_images.image_url`에 남긴다.

## 7. 완료 기준

- [x] 정상 흐름·주요 실패 흐름 동작 (제출 → 견적 → 재견적 → 수락 → 결제 → 제작 시작, 반려·취소 포함)
- [x] 입력 검증(form DTO)과 접근 권한 적용 (타인 요청·타인 링크 접근 차단)
- [x] 상태 전이·트랜잭션 규칙 준수 (`OrderStatus` 무변경, 견적·링크 전이는 각 enum이 소유)
- [x] 전용 테스트 통과 (Service + Controller, store 패턴). 만료·중복 결제·재견적 무효화 케이스 포함
- [x] enum ↔ DDL CHECK 동기화 테스트 (notification 패턴)
- [x] 관련 SQL·문서 함께 수정 (V13 + V0/V1 소급, README 현황표, TodoList, team-plan 8장)
