---
domain: coupon
owner: 정후
status: approved
approved-at: 2026-07-26
---

# 쿠폰 발급·적용 명세

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 관리자가 만든 할인 쿠폰을 회원에게 지급하고, 결제 시점에
  서버가 다시 계산한 할인액만큼 주문 금액을 깎고 사용·복구를 정확히 기록한다.
- 주요 유스케이스 (액터 → 행동 → 결과):
  1. 관리자 → `/admin/coupons`에서 쿠폰 등록(할인 유형·값·최소 주문액·최대 할인액·수량·기간)
     → `ACTIVE` 쿠폰이 생기고 고객 다운로드 목록에 노출된다.
  2. 관리자 → 특정 회원을 골라 **지정 발급** → 해당 회원의 `member_coupons` 행이 `AVAILABLE`로 생긴다.
  3. 관리자 → 쿠폰 **중단**(`SUSPENDED`) / **종료**(`ENDED`) → 신규 발급이 막힌다.
     이미 발급된 쿠폰은 사용 가능 여부 판정에서 함께 걸러진다.
  4. 고객 → `/coupons`에서 받을 수 있는 쿠폰을 **다운로드** → 정원 안에서 1인 1장 발급된다.
  5. 고객 → `/mypage/coupons`에서 사용 가능 / 사용 완료 / 기간 만료로 나뉜 쿠폰함을 본다.
  6. 고객 → `/orders/checkout`에서 쿠폰을 선택하고 결제 → 할인액이 반영된 금액으로 결제되고
     쿠폰이 `USED`로 확정된다.
  7. 고객 → 주문제작 견적을 수락한 뒤 결제 링크에서 쿠폰을 선택하고 결제 → 위와 동일하게 처리된다.
  8. 고객·관리자 → 주문 취소 → 쓴 쿠폰이 `AVAILABLE`로 복구된다(기간이 남아 있으면 재사용 가능).

범위 밖: 쿠폰 코드 입력형 발급, 자동 발급(가입·생일 등), 중복 사용, 부분 취소에 따른 부분 복구.

## 2. 상태값 (conventions.md 상태값 공통 규칙 준수)

| 컬럼 | 값(영문 enum 이름) | 시작 상태 | 최종 상태 | 전이 규칙 요약 |
|---|---|---|---|---|
| `coupons.status` | `CouponStatus` — `ACTIVE / SUSPENDED / ENDED` | `ACTIVE` (DDL DEFAULT) | `ENDED` | `ACTIVE ↔ SUSPENDED`(관리자가 노출을 껐다 켬), 둘 다 → `ENDED`(종료, 되돌리기 없음). 전이는 `CouponStatus.canTransitionTo()`가 소유 |
| `member_coupons.status` | `MemberCouponStatus` — `AVAILABLE / USED` | `AVAILABLE` (DDL DEFAULT) | 없음(복구 가능) | `AVAILABLE → USED`(결제 확정), `USED → AVAILABLE`(주문 취소 복구). 두 전이 모두 조건부 UPDATE 1회로만 수행한다 |

> 인벤토리에 열려 있던 `coupons.status`(`ACTIVE / INACTIVE / ENDED ?`)와
> `member_coupons.status`(`ISSUED / USED / EXPIRED`)를 여기서 확정했다. 변경 이유는 아래 두 가지다.
> - `INACTIVE` → **`SUSPENDED`**: `members.status`가 이미 "관리자가 잠시 막은 상태"에 `SUSPENDED`를 쓰고 있어
>   같은 의미에 같은 단어를 쓴다.
> - `ISSUED` → **`AVAILABLE`**, `EXPIRED` 제거: 아래 파생값 항목 참고.

- status가 아닌 것 점검:
  - **만료** → `coupons.expires_at < now`의 **파생값.** 별도 상태로 저장하지 않는다. 저장하면 만료 시각이
    지날 때마다 전 행을 갱신하는 배치가 필요하고, 배치가 밀리면 화면과 DB가 어긋난다.
    쿠폰함의 "기간 만료" 탭도 파생값으로 그린다.
  - **소진** → `issued_quantity >= total_quantity`의 파생값.
  - **다운로드 가능 여부** → 유효 기간 + 소진 + 이미 받았는지의 파생값.
  - **할인 유형**(`PERCENTAGE` / `FIXED_AMOUNT`) → `discount_type`, 종류지 상태가 아니다.
  - **주문 상태** → `orders.status`는 order 도메인 소유다. 쿠폰은 읽지도 쓰지도 않는다.

## 3. DB

- 사용할 테이블: `coupons`(V1에 이미 생성됨), `member_coupons`(V0 ERD에 설계만 있고 미생성)
- 스키마 변경 필요 여부: **필요** — `docs/sql/legacy/V15_coupon.sql` 신규 (V0_ERD.sql·V1_first_MVC_table.sql 소급 반영)

  1. `coupons`에 CHECK 추가 — `chk_coupons_status CHECK (status IN ('ACTIVE','SUSPENDED','ENDED'))`,
     `chk_coupons_discount_type CHECK (discount_type IN ('PERCENTAGE','FIXED_AMOUNT'))`.
     `status`·`discount_type` 타입을 `VARCHAR(30)` → `VARCHAR(20)`으로 좁힌다(상태 컬럼 규칙).
  2. `member_coupons` 신규 — 컬럼은 V0 ERD 원안 그대로.
     - `uk_member_coupons_coupon_member UNIQUE (coupon_id, member_id)` — **1인 1장의 1차 방어선**
     - `uk_member_coupons_applied_order UNIQUE (applied_order_id)` — **주문 1건에 쿠폰 1장**을 DB가 강제
     - `chk_member_coupons_status CHECK (status IN ('AVAILABLE','USED'))`
     - `idx_member_coupons_member_status (member_id, status)` — 쿠폰함·적용 가능 목록 조회
  3. 데모 시드 2건 — 정률 1건(10%, 최대 5,000원, 최소 20,000원), 정액 1건(3,000원, 최소 15,000원).

- `created_at`/`updated_at`/`issued_at`은 DDL DEFAULT에 위임한다. `used_at`은 업무 컬럼이라 서비스가 세팅한다.
- 금액 컬럼은 `DECIMAL(12,0)`, 자바에서는 `Long`으로 다룬다(원 단위 정수).
  `discount_value`만 `DECIMAL(12,2)`이지만 정률(퍼센트)·정액(원) 모두 정수로만 입력받는다.

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드 (`CouponService`) — order·payment는 **이 계약으로만** 쿠폰에 접근한다:
  - `List<AvailableCouponView> getApplicableCoupons(Long memberId, long originalAmount)`
    — 지금 이 금액에 쓸 수 있는 쿠폰과 각각의 예상 할인액. 화면 표시용
  - `CouponDiscount calculateDiscount(Long memberId, Long memberCouponId, long originalAmount)`
    — 검증 + 금액 계산. `memberCouponId`가 null이면 할인 0을 반환한다(미선택 정상 경로)
  - `void use(Long memberId, Long memberCouponId, Long orderId, long originalAmount)`
    — 결제 트랜잭션에 참여. 조건부 UPDATE 실패 시 예외 → 결제 전체 롤백
  - `void restoreByOrderId(Long orderId)` — 취소 트랜잭션에 참여. 쓴 쿠폰이 없으면 조용히 통과
  - `void download(Long memberId, Long couponId)` / `List<MyCouponView> getMyCoupons(Long memberId)`
    / `List<DownloadableCouponView> getDownloadableCoupons(Long memberId)`
- 내가 사용할 다른 도메인의 공개 Service 메서드 (Mapper 직접 호출 금지):
  - `MemberService` — 관리자 지정 발급 화면의 회원 조회·검증
  - 관리자 목록에서 쿠폰을 쓴 주문 정보가 필요하면 `OrderService.getOrderReferenceMap(orderIds)`
- **coupon은 `orders`·`members` 테이블을 JOIN하지 않는다.** `member_coupons.applied_order_id`·`member_id`는
  자기 테이블의 컬럼이므로 값만 저장하고, 표시용 정보는 상대 도메인 Service로 조합한다.
- 호출 방향: `payment → coupon`, `order → coupon` 단방향. coupon은 order·payment를 역참조하지 않는다.

## 5. 화면

- 대상 화면과 URL (목업 → 실구현 전환 시 URL·템플릿 유지):
  - 고객 `GET /mypage/coupons` → `customer/coupon/list.html` (쿠폰함, 목업 전환)
  - 고객 `GET /coupons` → `customer/coupon/available.html` (다운로드 가능 목록, 신규)
  - 고객 `POST /coupons/{couponId}/download`
  - 관리자 `GET /admin/coupons` → `admin/coupon/list.html` (목업 전환)
  - 관리자 `GET /admin/coupons/new`, `GET /admin/coupons/{id}/edit` → `admin/coupon/form.html` (신규)
  - 관리자 `POST /admin/coupons`, `POST /admin/coupons/{id}`,
    `POST /admin/coupons/{id}/status`, `POST /admin/coupons/{id}/issue`
  - 쿠폰 선택 UI가 붙는 기존 화면: `customer/order/checkout.html`, 주문제작 결제 링크 화면
- 목업 JS가 시연하는 임시 동작 중 규칙으로 확정할 것:
  - 목업 쿠폰함의 탭(사용 가능 / 사용 완료 / 기간 만료)은 **저장된 상태 3개가 아니다.**
    `status`(2개) + 만료 파생값의 조합으로 분류한다.
  - 목업의 "발급 중" 배지는 `ACTIVE` + 기간 내 + 미소진의 파생 라벨이다.
  - 체크아웃의 할인 금액 표시는 **표시 전용**이다. 결제 트랜잭션에서 서버가 다시 계산하며
    클라이언트가 보낸 할인액·최종 금액은 계산에도 저장에도 쓰지 않는다.
  - `scripts\import-customer-mockups.ps1`의 `$screenMap`에서 `coupon-list.html`을 **먼저 제외**한다.
    제외 전에 스크립트를 돌리면 실구현이 목업으로 덮인다.

## 6. 비즈니스 규칙 확정

- team-plan.md 8장에서 이 도메인과 관련된 항목: "쿠폰 사용 및 복구 시점" (미결)
- 확정한 규칙 (확정 후 team-plan 8장 표 갱신):
  1. **유효 판정.** 쓸 수 있는 쿠폰 = `coupons.status = 'ACTIVE'` AND `starts_at <= now < expires_at`
     AND `member_coupons.status = 'AVAILABLE'`. 세 조건은 조회와 사용 확정 시점에 각각 검증한다.
  2. **할인액 계산은 `DiscountType`이 소유한다**(`OrderStatus.canTransitionTo()`가 전이를 소유하는 것과 같은 방식).
     - `PERCENTAGE`: `floor(원가 × value / 100)`, `maximum_discount_amount`가 있으면 그 값으로 상한
     - `FIXED_AMOUNT`: `value`
     - 마지막에 **원가를 넘지 않게 clamp**한다. 최종 결제 금액은 항상 0 이상이다.
  3. **최소 주문 금액.** `originalAmount < minimum_order_amount`면 적용을 거부한다(`COUPON_005`).
     화면 목록에서도 애초에 선택지로 내보내지 않는다.
  4. **주문 1건에 쿠폰 1장, 쿠폰 1장은 주문 1건.** `uk_member_coupons_applied_order`가 DB에서 강제한다.
     중복 사용은 UNIQUE 충돌로 결제 전체가 롤백된다.
  5. **사용 확정 시점 = 결제 트랜잭션 안이다.**
     - 일반 주문: `CheckoutPaymentProcessor`가 주문·결제를 만드는 같은 트랜잭션에서 `use(...)`
     - 주문제작: `CustomOrderPaymentService.pay(...)`의 같은 트랜잭션에서 `use(...)`
     - 조건부 UPDATE(`WHERE id = ? AND status = 'AVAILABLE'`) 영향 행이 1이 아니면 예외 → 전체 롤백.
       체크아웃 화면에서 미리 잡아두지 않는 이유는, 결제까지 가지 않은 초안이 쿠폰을 계속 묶어버리기 때문이다.
  6. **복구 시점 = 주문 취소 트랜잭션 안이다.** `RefundService`가 주문 `CANCELED`·결제 `CANCELED`·환불·
     재고 복구와 같은 트랜잭션에서 `restoreByOrderId(orderId)`를 호출한다.
     `USED → AVAILABLE` + `applied_order_id = NULL` + `used_at = NULL`.
     복구된 쿠폰의 기간이 이미 지났다면 파생 만료로 자연히 못 쓴다. 기간을 연장해 주지 않는다.
  7. **다운로드 정원.** `UPDATE coupons SET issued_quantity = issued_quantity + 1
     WHERE id = ? AND status = 'ACTIVE' AND issued_quantity < total_quantity` 조건부 UPDATE로
     정원 초과를 막는다(product 재고 차감과 같은 패턴). 영향 행 0이면 `COUPON_007`(소진).
  8. **1인 1장.** 같은 쿠폰을 두 번 받을 수 없다. 앱에서 먼저 확인하고, 동시 요청은
     `uk_member_coupons_coupon_member` 충돌로 막는다(`COUPON_008`).
  9. **관리자 지정 발급도 같은 규칙을 탄다.** 정원과 1인 1장을 똑같이 적용하며, 발급 경로만 다르다.
     `SUSPENDED`·`ENDED` 쿠폰은 지정 발급도 막는다.
  10. **금액의 정본은 서버다.** 화면이 보낸 것은 `memberCouponId` 하나뿐이고, 원가·할인액·최종 금액은
      모두 서버가 다시 계산한다(order-payment.md·order-custom.md와 같은 규칙).
  11. **주문제작 요청서 단계에는 쿠폰을 적용하지 않는다.** 견적 전이라 확정 금액이 없기 때문이다.
      `CustomOrderService.submitRequest()`의 `discount_amount = 0`은 그대로 둔다.
      쿠폰은 **결제 링크 결제 시점**에만 붙고, 원가는 `custom_order_payment_links.amount`(견적 금액)다.
  12. **쿠폰 종료(`ENDED`)는 이미 발급된 쿠폰도 못 쓰게 한다.** 규칙 1의 유효 판정이 `coupons.status`를
      함께 보기 때문이다. 발급된 쿠폰 행을 일괄 갱신하지 않는다.

## 7. 완료 기준

- [x] 정상 흐름·주요 실패 흐름 동작 (등록 → 발급/다운로드 → 적용 결제 → 취소 복구, 소진·중복·만료 포함)
- [x] 입력 검증(form DTO)과 접근 권한 적용 (타인 쿠폰 사용 차단, 관리자 화면 권한)
- [x] 상태 전이·트랜잭션 규칙 준수 (사용·복구·정원 모두 조건부 UPDATE 1회)
- [x] 전용 테스트 통과 (Service + Controller + 화면 렌더링, store 패턴)
- [x] enum ↔ DDL CHECK 동기화 테스트 (notification 패턴)
- [x] 관련 SQL·문서 함께 수정 (V15 + V0/V1 소급, conventions 인벤토리, team-plan 8장, README, TodoList)
