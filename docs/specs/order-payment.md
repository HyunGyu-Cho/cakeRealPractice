---
domain: order-payment
owner: 주환
status: approved
approved-at: 2026-07-25
---

# 일반 주문·결제 명세

> **📄 스펙 문서가 뭔가요?** — 코드를 쓰기 **전에** "무엇을, 어떤 규칙으로 만들지" 먼저
> 못 박아 둔 설계 합의서다. 이 문서가 `status: approved`가 되기 전에는 훅이 구현 코드 작성을
> 막는다. 나중에 "왜 이렇게 만들었지?"의 답이 여기 있다.
>
> **이 도메인을 한 줄로** — 정해진 가격의 일반 상품을 주문하고 결제하는 흐름.
>
> 모르는 용어가 나오면 [용어 사전](../conventions.md#용어-사전)을 본다.

## 범위

장바구니에서 선택한 일반 상품을 픽업 설정 → 주문서 → 결제로 처리한다. 결제 성공 전에는
`orders`를 생성하지 않으며, 로그인 세션의 UUID 체크아웃 초안을 `payments.idempotency_key`로 사용한다.
부분 취소는 후속 범위다.

결제 제공자는 설정(`cakeshop.payment.provider`)으로 고른다. `mock`(기본)은 외부 호출 없이 즉시 승인하고,
`toss`는 토스페이먼츠 결제창 + 승인 API를 쓴다. **두 경로의 도메인 로직은 같고** 차이는
`PaymentGateway` 구현 하나뿐이다. 테스트·CI·스모크는 `mock`으로 돈다.

## 픽업 규칙

- 선택 상품의 최대 `preparation_days` 이후부터 오늘 기준 14일 이내만 선택한다.
  (14일은 **일반 주문 기준**이다. 결제 시 재고가 묶이므로 짧게 유지한다. 재고를 관리하지 않는
  주문제작은 90일 창을 쓴다 — `docs/specs/order-custom.md` 6장 규칙 9)
- **한 슬롯에는 주문 1건만** 받는다. 이미 픽업이 잡힌 시각은 슬롯 목록에서 빠지고,
  그 시각을 직접 요청해도 `STORE_008`로 거부된다. 취소·반려된 주문의 슬롯은 다시 열린다.
- 픽업 예정 주문이 있는 날짜는 **휴무일로 지정할 수 없다**(`STORE_009`, 건수와 함께 안내).
- 매장 영업일, 특정 휴무일, 영업시간, 픽업 운영시간의 교집합에서 설정 간격 슬롯만 허용한다.
- 종료 시각은 포함하지 않는다. 당일 슬롯은 현재 시각보다 뒤인 것만 허용한다.
- 화면 진입, 단계 이동, 결제 트랜잭션 직전에 서버가 같은 규칙을 다시 검증한다.

## 결제 흐름 (준비 → 확정 2단계)

외부 승인은 리다이렉트로 돌아오므로 결제가 두 요청으로 나뉜다.

**1단계 — 준비** (`GET /orders/payment`)

1. 체크아웃 UUID와 회원 소유권을 확인하고 픽업·재고·판매상태를 다시 검증한다.
2. 서버가 금액을 재계산한다. 클라이언트가 보낸 금액은 계산이나 저장에 사용하지 않는다.
3. `payments`에 `status=READY` 행을 선삽입한다 — `toss_order_id = ORD-yyyyMMdd-랜덤값`,
   `idempotency_key = 체크아웃 UUID`, `amount = 재계산한 최종 금액`, `order_id = NULL`, `payment_key = NULL`.
   같은 체크아웃으로 다시 들어오면 기존 READY 행을 재사용한다(금액이 바뀌었으면 갱신).
4. 화면은 이 `toss_order_id`·금액·클라이언트 키로 결제창을 연다.

주문을 아직 만들지 않으므로 **`payments.order_id`는 NULL을 허용**한다(V17).

**2단계 — 확정** (`GET /orders/payment/success?paymentKey&orderId&amount`)

1. `toss_order_id`로 READY 결제를 찾고 소유자를 확인한다.
2. **콜백 금액과 저장된 금액을 대조**한다. 다르면 승인하지 않고 `PAYMENT_001`로 중단한다.
3. 이미 `DONE`이면 승인하지 않고 기존 주문을 반환한다(새로고침·뒤로가기 멱등).
4. 트랜잭션 **밖에서** 승인 API를 호출한다. 외부 I/O를 DB 트랜잭션에 넣지 않는다.
5. 한 트랜잭션으로 확정한다 — 재고 차감 → `PAID` 주문과 스냅샷 저장 → 쿠폰 사용 →
   결제 행 갱신(`order_id`, `payment_key`, 결제수단, `status=DONE`, `provider_status`, `approved_at`) →
   선택 장바구니 항목 삭제 → 알림.
6. `/orders/complete?orderId=`로 리다이렉트한다(PRG).

**보상** — 5가 실패하면 승인은 이미 성사됐으므로 자동으로 취소 API를 호출하고 결제를 `ABORTED` +
`failure_code/message`로 마감한 뒤, 고객에게 "결제가 자동 취소됐다"고 안내한다. 돈이 남는 상태를 만들지 않는다.

**실패 콜백** (`GET /orders/payment/fail?code&message&orderId`) — READY 행을 `ABORTED`로 마감하고
결제 화면을 오류 메시지와 함께 다시 렌더한다.

결제 UUID UNIQUE 충돌은 롤백 후 기존 결제의 주문을 반환한다.

## 외부 상태 매핑

`PaymentStatus` 6개는 늘리지 않고 제공자 상태를 매핑한다. 원본 문자열은 `provider_status`에 그대로 남긴다.

| 제공자 상태 | `PaymentStatus` |
|---|---|
| `READY`, `IN_PROGRESS`, `WAITING_FOR_DEPOSIT` | `READY` |
| `DONE` | `DONE` |
| `CANCELED` | `CANCELED` |
| `PARTIAL_CANCELED` | `PARTIAL_CANCELED` |
| `ABORTED` | `ABORTED` |
| `EXPIRED` | `EXPIRED` |

## 웹훅

`POST /webhooks/toss`는 인증·CSRF 예외 경로다. 전송 ID를 `webhook_events.event_id` UNIQUE로 잡아
같은 이벤트가 여러 번 와도 1건만 저장하고, **저장 즉시 200**을 돌려준다(10초 제한). 반영은 저장과 분리한다.

승인 결과는 확정 단계가 이미 반영하므로 웹훅의 역할은 **밀린 상태 따라잡기**(가상계좌 입금, 외부 취소 등)다.
우리 상태가 이미 최신이면 `SKIPPED`로 남긴다.

## 상태 대조 배치

`READY`로 일정 시간 이상 방치된 결제를 조회 API로 대조해 실제 상태에 맞춘다. 결제 키 없이 만료된
준비 행은 `EXPIRED`로 마감한다. 고객이 결제창을 닫아버린 경우가 주 대상이다.

## 취소·환불

- 고객은 본인 주문의 `PAID`, `READY_FOR_PICKUP`만 취소할 수 있다.
- 취소 기한은 상품별 `pickup_at - cancellation_limit_days` 중 가장 이른 시각이며 경계부터 불가하다.
- 관리자는 같은 두 상태를 기한과 무관하게 취소할 수 있다. `PICKED_UP` 이후에는 불가하다.
- 부분 취소는 지원하지 않는다.
- 검증 → 외부 취소 호출 → 확정 순서로 처리한다. 승인과 같은 이유로 외부 호출은 트랜잭션 밖이다.
- 주문 `CANCELED`, 결제 `CANCELED`, 환불 `DONE`, 정확한 재고 복구를 한 트랜잭션으로 처리한다.
- 주문 행 잠금과 결정적 취소 멱등 키(`CANCEL-ORDER-{orderId}`)로 반복 요청의 재고 이중 복구를 막는다.
  같은 키를 외부 취소 요청의 멱등 키로도 넘겨 제공자 쪽 이중 취소도 막는다. 장바구니는 복원하지 않는다.

## 상태

- 일반 주문: `PAID → READY_FOR_PICKUP → PICKED_UP`
- 예외: `PAID|READY_FOR_PICKUP → CANCELED`
- 결제: `READY → DONE → CANCELED`, 실패·이탈은 `READY → ABORTED|EXPIRED`
- 환불: `REQUESTED / DONE / REJECTED` 중 승인 응답을 받은 취소는 즉시 `DONE`

## 화면

- 고객: `/orders/pickup`, `/orders/checkout`, `/orders/payment`,
  `/orders/payment/success`, `/orders/payment/fail`,
  `/orders/complete?orderId=...`, `/orders/{orderId}`
- 관리자: `/admin/orders`, `/admin/orders/{orderId}`, `/admin/fulfillment`, `/admin/payments`

관리자 결제 Mapper는 주문·회원 테이블을 JOIN하지 않고 Order/Member Service의 배치 조회 결과를 조합한다.
