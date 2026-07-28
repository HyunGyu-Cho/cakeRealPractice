# 6인 협업 시 충돌 예상 지점

## 문서 목적

이 문서는 이 프로젝트를 6명이 도메인별로 나누어 동시에 개발한다고 가정했을 때, 사전 합의가 없으면 충돌하거나 통합 장애가 발생할 지점을 정리한다.

여기서 말하는 충돌은 두 종류다.

- **물리적 충돌**: 같은 파일이나 Flyway 버전을 동시에 변경해 발생하는 Git·빌드 충돌
- **계약 충돌**: Git 병합은 성공했지만 도메인별 상태값·처리 순서·데이터 의미가 달라 기능이 깨지는 충돌

이 프로젝트에서는 물리적 충돌보다 주문·결제·쿠폰·재고처럼 여러 도메인이 연결되는 계약 충돌이 더 위험하다.

코드 작성 방법은 [`conventions.md`](conventions.md), 확정된 업무 규칙은 [`business-rules.md`](business-rules.md), DB 변경 방법은 [`database.md`](database.md)를 함께 참고한다.

---

## 결론

도메인 폴더를 담당자별로 분리하더라도 아래 항목은 독립적으로 결정할 수 없다.

| 우선순위 | 충돌 지점 | 합의가 없을 때 생기는 문제 | 먼저 정할 것 |
|---|---|---|---|
| P0 | 주문·결제·장바구니·상품·쿠폰 트랜잭션 | 결제만 성공하거나 재고·쿠폰·장바구니 중 일부만 변경됨 | 트랜잭션 소유자, 처리 순서, 실패 보상 |
| P0 | 주문·결제 상태값 | Java enum, DB 제약, 화면과 통계가 서로 다른 상태를 사용 | 상태 이름, 허용 전이, 최종 상태 |
| P0 | 가격·재고·매출의 정본 | 화면과 주문 금액이 다르거나 매출 집계가 이중화됨 | 현재값과 스냅샷, 재고 차감 시점, 매출 원천 |
| P0 | Flyway 마이그레이션 | 같은 버전 번호가 생기거나 체크섬 불일치로 기동 실패 | 번호 할당, DB 오너, 적용 파일 불변 규칙 |
| P1 | 도메인 간 공개 계약 | DTO와 메서드가 어긋나거나 순환 의존성이 발생 | 공개 Service 인터페이스와 변경 절차 |
| P1 | 알림 이벤트 | 이벤트 이름·수신자·발행 시점이 도메인마다 달라짐 | 이벤트 목록, 팬아웃, 트랜잭션 경계 |
| P1 | 공통 UI | CSS 덮어쓰기, JS 중복 실행, 메뉴 병합 충돌 | 공통 파일 오너와 화면 전용 파일 분리 |
| P1 | 보안 설정 | 공개 화면이 잠기거나 상태 변경 API가 노출됨 | URL 접근 권한표와 CSRF 예외 |
| P2 | 홈·사이드바·시드·환경 설정 | 여러 기능이 같은 통합 파일을 동시에 수정 | 통합 담당자와 공통 파일 PR 규칙 |

---

## P0. 구현 전에 반드시 합의할 지점

### 1. 결제 확정 트랜잭션

현재 일반 주문의 결제 확정 흐름은 다음 작업을 하나의 DB 트랜잭션으로 묶는다.

```text
결제 금액 재검증
→ 상품 재고 차감
→ 주문과 주문 항목 생성
→ 쿠폰 사용 확정
→ 결제 상태 DONE 확정
→ 결제한 장바구니 항목 삭제
→ 고객·관리자 알림 저장
```

실제 순서는 `CheckoutPaymentProcessor.confirm()`에서 확인할 수 있다.

- [`CheckoutPaymentProcessor.java`](../src/main/java/com/cakeshop/domain/payment/service/CheckoutPaymentProcessor.java)
- [`PaymentFacade.java`](../src/main/java/com/cakeshop/domain/payment/service/PaymentFacade.java)

외부 결제 승인 호출은 DB 트랜잭션 밖에서 수행한다. 승인은 성공했지만 내부 확정 트랜잭션이 실패하면 `PaymentFacade`가 결제 제공자에게 보상 취소를 요청한다.

담당자가 분리되면 다음 판단이 쉽게 달라진다.

- 재고를 주문 생성 때 차감할지 결제 성공 때 차감할지
- 쿠폰을 체크아웃 진입 때 예약할지 결제 성공 때 사용할지
- 장바구니를 주문서 생성 때 비울지 결제 확정 후 비울지
- 결제 승인 후 주문 생성 실패를 어느 도메인이 복구할지
- 새로고침·중복 요청에 대한 멱등성을 어느 도메인이 보장할지

#### 필요한 합의

- 결제 확정 트랜잭션은 payment의 `CheckoutPaymentProcessor`가 소유한다.
- 외부 결제 제공자 호출은 DB 트랜잭션 밖에서 실행한다.
- 재고·주문·쿠폰·결제·장바구니 변경은 하나의 트랜잭션에서 성공하거나 모두 롤백한다.
- 외부 승인 후 내부 확정이 실패하면 payment가 보상 취소를 책임진다.
- 관련 공개 Service 시그니처를 변경할 때 cart·order·payment·coupon·product 담당자가 함께 검토한다.

---

### 2. 주문과 결제 상태값

`OrderStatus`는 일반 주문과 주문제작 주문이 함께 사용한다.

```text
일반 주문:
PAID → READY_FOR_PICKUP → PICKED_UP

주문제작:
UNDER_REVIEW → IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP

취소·반려:
최종 상태 전까지 → CANCELED
UNDER_REVIEW → REJECTED
```

정본은 [`OrderStatus.java`](../src/main/java/com/cakeshop/domain/order/entity/OrderStatus.java)의 값과 `canTransitionTo()`다.

결제 상태는 주문 상태와 분리한다.

```text
READY / DONE / CANCELED / PARTIAL_CANCELED / ABORTED / EXPIRED
```

상태 이름 하나가 바뀌면 다음 항목을 함께 변경해야 한다.

- Java enum
- Flyway DDL의 `CHECK` 제약
- MyBatis Mapper 조건
- 서비스의 상태 전이 검증
- 관리자 상태 변경 화면
- 알림 종류와 문구
- 통계 포함·제외 조건
- 상태 동기화 테스트

#### 필요한 합의

- DB에는 enum의 영문 `name()`을 저장하고 한글 라벨은 화면에서만 사용한다.
- 상태 전이 규칙은 Service와 enum이 소유한다.
- DB `CHECK`는 허용 값 집합을 보장하고 업무 전이는 Service가 검증한다.
- 주문 상태와 결제 상태를 섞지 않는다.
- enum과 DDL은 같은 PR에서 변경한다.

자세한 상태값 규칙은 [`conventions.md`](conventions.md)의 상태값 공통 규칙을 따른다.

---

### 3. 가격·재고·매출의 정본

도메인마다 같은 데이터를 별도로 저장하면 시간이 지나면서 값이 어긋난다. 따라서 어떤 도메인의 값이 정본인지 먼저 정해야 한다.

| 데이터 | 정본과 처리 방식 |
|---|---|
| 장바구니 가격 | 장바구니에 저장하지 않고 상품의 현재 판매 정보를 조회 |
| 주문 가격 | 결제 성공 시 상품명·유형·가격·준비 기간·취소 제한을 주문 항목에 스냅샷 |
| 재고 | product가 소유하고 결제 확정 시 조건부 차감, 전액 취소 시 복구 |
| 품절 여부 | `stock_quantity`에서 파생하며 상품 판매 상태와 분리 |
| 주문 금액 | 주문 시점의 `orders.final_amount` |
| 순매출 | payment의 승인 금액에서 완료된 취소 금액을 차감 |

합의가 없으면 다음과 같은 구현이 동시에 생길 수 있다.

- cart 담당자는 장바구니에 가격을 저장하고 order 담당자는 현재 상품 가격을 사용한다.
- order 담당자는 상품을 실시간 조회하고 화면 담당자는 주문 스냅샷을 표시한다.
- statistics 담당자는 주문 금액을 매출로 사용하고 payment 담당자는 실제 승인·취소 금액을 사용한다.
- product 담당자는 재고 0과 `INACTIVE`를 하나의 상태로 취급한다.

확정된 판단은 [`business-rules.md`](business-rules.md)의 장바구니·주문·결제·통계 규칙을 따른다.

---

### 4. Flyway 마이그레이션

여러 브랜치가 같은 기준 커밋에서 시작하면 모두 현재 최대 번호 다음의 같은 버전을 선택할 수 있다.

예를 들어 두 담당자가 모두 `V4`를 만들면 파일명이 달라 Git 병합은 성공할 수 있지만, Flyway는 같은 버전이 두 개라고 판단해 애플리케이션 기동을 중단한다.

이미 적용된 마이그레이션을 수정하면 해당 파일을 먼저 실행한 팀원의 DB에서 체크섬 불일치가 발생한다.

#### 필요한 합의

- DB 변경 전에 마이그레이션 번호를 선점한다.
- 또는 DB 오너가 PR 병합 직전에 번호를 부여한다.
- Git에 올라간 버전 마이그레이션은 수정하지 않는다.
- 기존 변경을 되돌릴 때도 새 마이그레이션을 추가한다.
- enum을 추가하거나 변경할 때 Java enum과 DB `CHECK`를 함께 검토한다.
- 공용 개발 시드인 [`R__dev_seed.sql`](../src/main/resources/db/seed/R__dev_seed.sql)은 멱등성을 유지한다.

구체적인 작성·반영 절차는 [`database.md`](database.md)를 따른다.

---

## P1. 통합 과정에서 반드시 만나는 지점

### 5. 도메인 간 공개 Service 계약

다른 도메인의 Mapper나 테이블을 직접 사용하면 상대 도메인의 내부 구현에 결합된다. 이 프로젝트에서는 합의된 공개 Service를 통해서만 도메인 간 데이터를 주고받는다.

주요 연결은 다음과 같다.

| 호출 방향 | 필요한 정보 |
|---|---|
| cart → product | 판매 여부, 현재 가격, 재고 |
| order → cart·product·coupon·store | 결제 대상, 스냅샷 정보, 할인, 픽업 가능 시간 |
| payment → order | 결제 가능 여부, 주문 생성·상태 변경 |
| review → order | 본인 구매와 픽업 완료 여부 |
| chat → member·order | 상담 대상과 주문 참조 정보 |
| notification ← order·payment·chat·review | 알림 수신자와 이벤트 정보 |
| statistics → 각 도메인 | 도메인별 집계 결과 |

대표적인 순환 의존성 위험은 store와 order 사이에 있다.

- order는 픽업 가능 시간을 계산하기 위해 store 정보가 필요하다.
- store는 휴무일 등록을 막기 위해 기존 order 예약 현황이 필요하다.

이를 직접 Service 상호 호출로 구현하면 순환 의존성이 생긴다. 현재는 store가 [`PickupReservationPort`](../src/main/java/com/cakeshop/domain/store/service/PickupReservationPort.java)를 정의하고 order의 [`OrderPickupReservationProvider`](../src/main/java/com/cakeshop/domain/order/service/OrderPickupReservationProvider.java)가 구현한다.

#### 필요한 합의

- 공개 인터페이스는 데이터를 소유하거나 판단을 요청하는 도메인이 정의한다.
- 호출자는 상대 도메인의 Entity나 Mapper를 직접 사용하지 않는다.
- 공개 DTO에는 호출자에게 필요한 최소 데이터만 포함한다.
- 시그니처 변경은 모든 소비자를 검색하고 관련 담당자 승인을 받는다.
- 여러 도메인이 참여하는 쓰기 작업은 트랜잭션 소유자를 먼저 결정한다.

---

### 6. 알림 이벤트

주문·결제·주문제작·취소·채팅·후기 기능은 모두 알림을 생성한다. 이벤트 종류는 [`NotificationType.java`](../src/main/java/com/cakeshop/domain/notification/entity/NotificationType.java)가 단일 정본이다.

합의가 필요한 항목은 다음과 같다.

- 이벤트 이름
- 고객용과 관리자용 이벤트 구분
- 고객 본인 또는 전체 관리자 중 수신자 범위
- 동일 사건의 중복 발행 방지 기준
- 업무 트랜잭션과 알림 저장의 관계
- WebSocket 전송 실패 시 재시도와 업무 롤백 여부

#### 현재 기준

- 알림 INSERT는 원래 업무 트랜잭션과 함께 처리한다.
- 실시간 푸시는 커밋 후 개인 큐로 전송한다.
- 푸시 실패는 주문·결제를 롤백시키지 않는다.
- 전송 실패 이력은 별도 전달 테이블에 남기고 스케줄러가 재시도한다.
- 관리자 대상 이벤트는 전체 관리자에게 팬아웃한다.

알림 종류를 추가할 때는 enum, DDL 제약, 발행 위치, 화면 필터와 테스트를 함께 변경한다.

---

### 7. 공통 UI 파일

여러 화면이 아래 공통 파일을 공유한다.

- `static/css/app.css`
- `static/js/app.js`
- `templates/fragments/common/head.html`
- `templates/fragments/common/header.html`
- `templates/fragments/admin/sidebar.html`
- `templates/fragments/admin/header.html`
- `templates/fragments/customer/gnb.html`

사전 규칙이 없으면 화면 담당자가 자기 기능을 완성하기 위해 공통 CSS와 JS에 화면 전용 코드를 추가하게 된다.

이 프로젝트에서는 실제로 다음 문제가 발생한 흔적이 테스트에 기록돼 있다.

- `app.js` 중복 로드로 확인창이 두 번 표시됨
- 목업 CSS가 공통 디자인 토큰을 덮어씀
- `app.css`와 `chat.css`의 채팅 스타일 값이 서로 달랐음
- 공통 head를 사용하지 않은 화면에서 favicon·viewport·CSRF meta가 누락됨

회귀 방지 기준은 [`CustomerPageControllerTests.java`](../src/test/java/com/cakeshop/customer/CustomerPageControllerTests.java)에서 확인할 수 있다.

#### 필요한 합의

- 공통 스타일과 동작만 `app.css`·`app.js`에 둔다.
- 화면 전용 스타일과 스크립트는 기능별 파일로 분리한다.
- `app.js`는 공통 head에서 한 번만 로드한다.
- 모든 화면은 공통 head 프래그먼트를 사용한다.
- 관리자 메뉴 추가는 [`sidebar.html`](../src/main/resources/templates/fragments/admin/sidebar.html)의 단일 PR 또는 통합 담당자를 통해 처리한다.

---

### 8. 인증·인가와 CSRF

모든 URL의 접근 정책은 [`SecurityConfig.java`](../src/main/java/com/cakeshop/global/security/SecurityConfig.java)에 집중돼 있다.

주요 구분은 다음과 같다.

| 구간 | 예시 |
|---|---|
| 공개 | 메인, 로그인, 회원가입, 상품 조회, 커뮤니티 조회 |
| 회원 전용 | 장바구니, 주문, 마이페이지, 쿠폰, 후기, 채팅 |
| 관리자 전용 | `/admin/**` |
| 인증 사용자 공통 | 고객·관리자 알림 |
| CSRF 예외 | 토스 웹훅 POST |

Spring Security의 matcher는 선언 순서가 우선순위가 된다. 각 담당자가 컨트롤러만 추가하면 다음 문제가 생길 수 있다.

- 공개 GET 화면이 기본 인증 규칙에 걸림
- 관리자 API가 일반 회원에게 허용됨
- 고객과 관리자 모두 쓰는 알림이 한 역할에만 허용됨
- 외부 웹훅이 CSRF에 막힘
- 문제 해결을 위해 CSRF 전체를 비활성화함

전체 경로 분류는 [`CustomerAuthGateE2ETests.java`](../src/test/java/com/cakeshop/e2e/CustomerAuthGateE2ETests.java)가 회귀를 방지한다.

#### 필요한 합의

- 새 URL을 추가할 때 공개·회원·관리자·공통 중 하나로 분류한다.
- 상태 변경 요청은 원칙적으로 CSRF 보호를 유지한다.
- 외부 웹훅처럼 필요한 경로만 정확히 CSRF 예외로 둔다.
- 도메인별 보안 테스트와 전체 접근 구간 테스트를 함께 갱신한다.

---

## P2. 공통 통합 파일

### 9. 홈·사이드바·설정·개발 시드

다음 파일은 특정 도메인의 소유로 보기 어렵고 여러 담당자가 동시에 수정할 가능성이 높다.

| 파일 또는 영역 | 충돌 이유 |
|---|---|
| `HomeService`·메인 화면 | 상품·후기·커뮤니티·쿠폰·매장 공개 데이터를 조합 |
| 관리자 sidebar | 모든 관리자 도메인의 메뉴를 한 파일에 표시 |
| `application.yml` | DB·파일·채팅·결제·스케줄러 설정이 집중 |
| `R__dev_seed.sql` | 여러 도메인의 로컬 테스트 데이터를 한 파일에 구성 |
| 공통 E2E 테스트 | 모든 도메인의 경로와 전체 사용자 흐름을 한자리에서 검증 |

#### 필요한 합의

- 공통 파일 오너 또는 통합 담당자를 정한다.
- 도메인 PR에서는 필요한 통합 변경을 별도 커밋으로 분리한다.
- 공통 파일 변경은 관련 도메인 담당자가 함께 검토한다.
- 로컬 접속 정보와 비밀값은 `.env`로 관리하고 공통 설정 파일에 개인 값을 넣지 않는다.
- 시드는 자동 증가 ID를 고정값으로 가정하지 않고 반복 실행 가능하게 작성한다.

---

## 권장 오너십

6명이 도메인을 나누더라도 공통 영역은 별도 오너십이 필요하다.

| 영역 | 권장 오너십 |
|---|---|
| 주문·결제 상태 및 체크아웃 오케스트레이션 | order·payment 담당자가 주 오너, cart·coupon·product 담당자가 필수 리뷰 |
| Flyway와 공용 DB 반영 | DB 오너 1명, 파괴적 변경은 2명 이상 리뷰 |
| `global/security` | 보안 오너 1명 |
| 공통 CSS·JS·프래그먼트 | 프론트 공통 오너 1명 |
| 알림 이벤트 목록 | notification 담당자가 정본 관리, 이벤트 생산 도메인이 함께 리뷰 |
| 홈·통계·전체 E2E | 후반 통합 담당자 또는 순환 담당 |

오너는 모든 코드를 직접 작성하는 사람이 아니라, 공통 계약 변경을 확인하고 충돌을 조정하는 사람이다.

---

## PR 전 확인 목록

### 모든 기능 PR

- [ ] 새 URL의 공개·회원·관리자 접근 수준을 정했다.
- [ ] 다른 도메인의 Mapper나 테이블을 직접 사용하지 않았다.
- [ ] 공개 Service 또는 DTO 시그니처 변경 시 모든 소비자를 확인했다.
- [ ] 상태값 변경이 enum·DDL·Mapper·화면·테스트에 함께 반영됐다.
- [ ] 공통 CSS·JS에 화면 전용 코드가 들어가지 않았다.
- [ ] 필요한 Service 업무 규칙과 Controller 검증·PRG 테스트가 있다.

### DB 변경 PR

- [ ] 마이그레이션 번호가 다른 작업과 중복되지 않는다.
- [ ] 이미 커밋되거나 적용된 마이그레이션을 수정하지 않았다.
- [ ] Java enum과 DB `CHECK` 값이 일치한다.
- [ ] 로컬 빈 DB에서 전체 마이그레이션을 재생했다.
- [ ] 개발 시드가 반복 실행 가능하다.

### 주문·결제 관련 PR

- [ ] 외부 결제 호출이 DB 트랜잭션 밖에 있다.
- [ ] 결제 확정 실패 시 보상 취소 경로가 유지된다.
- [ ] 재고·주문·쿠폰·결제·장바구니 변경이 원자적으로 처리된다.
- [ ] 중복 요청과 새로고침에 대한 멱등성을 검증했다.
- [ ] 취소 시 재고와 쿠폰 복구가 함께 검증된다.
- [ ] 알림 저장과 커밋 후 전송의 경계가 유지된다.

---

## 한 줄 요약

**6명이 도메인을 나눠도 결제 트랜잭션, 상태값, 데이터 정본, 마이그레이션, 공개 인터페이스와 공통 파일은 나눌 수 없다. 이 지점은 구현 전에 오너와 계약을 먼저 정해야 한다.**
