---
domain: order-payment
owner: 주환
status: approved
last_updated: 2026-07-25
---

# 일반 주문·모의 결제 명세

## 범위

장바구니에서 선택한 일반 상품을 픽업 설정 → 주문서 → 모의 결제로 처리한다. 결제 성공 전에는
`orders`를 생성하지 않으며, 로그인 세션의 UUID 체크아웃 초안을 `payments.idempotency_key`로 사용한다.
실제 토스 승인 API, 웹훅 보상, 쿠폰, 부분 취소, 주문제작 상품은 후속 범위다.

## 픽업 규칙

- 선택 상품의 최대 `preparation_days` 이후부터 오늘 기준 14일 이내만 선택한다.
- 매장 영업일, 특정 휴무일, 영업시간, 픽업 운영시간의 교집합에서 설정 간격 슬롯만 허용한다.
- 종료 시각은 포함하지 않는다. 당일 슬롯은 현재 시각보다 뒤인 것만 허용한다.
- 화면 진입, 단계 이동, 결제 트랜잭션 직전에 서버가 같은 규칙을 다시 검증한다.

## 결제 트랜잭션

1. 체크아웃 UUID와 회원 소유권을 확인한다.
2. `CartService.getCheckoutItems()`로 선택 항목, 판매 상태, 최신 가격과 재고를 다시 조회한다.
3. 상품별 조건부 UPDATE로 재고를 차감한다.
4. `PAID` 주문과 상품명·유형·가격·준비일·취소 기한 스냅샷을 저장한다.
5. `DONE` 모의 결제를 저장한다. 결제 키는 `MOCK-UUID`, 주문번호는 `ORD-yyyyMMdd-랜덤값`이다.
6. 선택 장바구니 항목을 삭제한다.

어느 단계든 실패하면 전부 롤백한다. 결제 UUID UNIQUE 충돌은 롤백 후 기존 결제의 주문을 반환한다.
클라이언트가 보낸 금액은 계산이나 저장에 사용하지 않는다.

## 취소·환불

- 고객은 본인 주문의 `PAID`, `READY_FOR_PICKUP`만 취소할 수 있다.
- 취소 기한은 상품별 `pickup_at - cancellation_limit_days` 중 가장 이른 시각이며 경계부터 불가하다.
- 관리자는 같은 두 상태를 기한과 무관하게 취소할 수 있다. `PICKED_UP` 이후에는 불가하다.
- 부분 취소는 지원하지 않는다.
- 주문 `CANCELED`, 결제 `CANCELED`, 환불 `DONE`, 정확한 재고 복구를 한 트랜잭션으로 처리한다.
- 주문 행 잠금과 결정적 취소 멱등 키로 반복 요청의 재고 이중 복구를 막는다. 장바구니는 복원하지 않는다.

## 상태

- 일반 주문: `PAID → READY_FOR_PICKUP → PICKED_UP`
- 예외: `PAID|READY_FOR_PICKUP → CANCELED`
- 결제: `DONE → CANCELED`
- 환불: `REQUESTED / DONE / REJECTED` 중 모의 결제는 즉시 `DONE`

## 화면

- 고객: `/orders/pickup`, `/orders/checkout`, `/orders/payment`,
  `/orders/complete?orderId=...`, `/orders/{orderId}`
- 관리자: `/admin/orders`, `/admin/orders/{orderId}`, `/admin/fulfillment`, `/admin/payments`

관리자 결제 Mapper는 주문·회원 테이블을 JOIN하지 않고 Order/Member Service의 배치 조회 결과를 조합한다.
