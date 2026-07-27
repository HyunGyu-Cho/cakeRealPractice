# 백엔드 공통 규약

각 담당자가 도메인을 독립적으로 구현하되 통합 시 충돌이 없도록 코드 스타일과 구조를 통일한다. 이 문서의 기준 구현체는 `com.cakeshop.domain.store` 도메인이며, 판단이 서지 않으면 store 코드를 그대로 따른다.

상태값(`status`) 표준은 이 문서 끝의 [상태값(status) 공통 규칙](#상태값status-공통-규칙)에 정본으로 둔다.

## 스택

- Spring Boot + MyBatis + MariaDB
- 서버 사이드 렌더링(Thymeleaf), 관리자/고객 화면 분리
- 화면 규격은 [frontend-template-format.md](frontend-template-format.md)를 따른다.

## 패키지 구조

도메인마다 아래 구조를 동일하게 사용한다. (`store`가 표준 예시다.)

```
domain/<도메인>/
  controller/      @Controller, 화면 요청 처리
  service/         @Service, 업무 로직·트랜잭션
  mapper/          @Mapper 인터페이스 (SQL은 XML에 둔다)
  entity/          DB 한 행을 표현하는 영속 모델(POJO)
  dto/
    form/          화면 입력 + 검증 전용 DTO
    view/          화면 출력 전용 읽기 DTO(record)
  error/           도메인별 ErrorCode enum
```

MyBatis XML은 `resources/mapper/<도메인>/XxxMapper.xml`에 둔다.

계층 호출 방향은 `Controller → Service → Mapper` 단방향이며, 역방향 호출이나 계층 건너뛰기는 하지 않는다.

### 도메인에서 다시 만들지 말 것 (global이 이미 갖고 있다)

같은 판단을 도메인마다 복사하면 한 곳만 빠뜨렸을 때 조용히 어긋난다. 아래는 전 도메인 공용이다.

| 필요한 일 | 쓰는 것 |
|---|---|
| 파일 저장·삭제 | `global/infra/FileStorageClient` — 저장 확장자는 **파일명이 아니라 content type에서 역산**한다(공개 서빙 경로에서 실행 가능한 파일이 생기지 않게). |
| 업로드 이미지 검증 | `global/infra/ImageValidator` — 형식 화이트리스트 + 크기 + **실제 파일 머리 바이트**. 예외를 던지지 않고 위반 사유를 돌려주므로, 받아서 자기 `ErrorCode`로 예외를 만든다. |
| 롤백 시 파일 정리 | `global/infra/StoredFileCleanup` — 파일 시스템은 트랜잭션에 참여하지 않는다. `@Transactional` 안에서 파일을 쓰면 반드시 등록한다. |
| 페이지 링크 쿼리 | `global/common/paging/PageQuery` — `fragments/common/pagination.html`의 `extraQuery` 입력을 만든다(인코딩 포함). |
| 페이지 요청·응답 | `global/common/paging/PageRequest`·`PageResult` |
| 결제 승인·취소·조회 | `domain/payment/infra/PaymentGateway` — 구현(`mock`/`toss`)은 설정으로 갈린다. **외부 호출은 DB 트랜잭션 밖**에서 하고, 승인 후 확정이 실패하면 호출측이 보상 취소를 책임진다. |

## 데이터베이스 규약

- **PK**: `BIGINT AUTO_INCREMENT`, 컬럼명 `id`, 자바 타입 `Long`.
- **네이밍**: 테이블·컬럼은 `snake_case`, 테이블명은 복수형(`members`, `products`). 자바 필드는 `camelCase`. 매핑은 `application.yml`의 `map-underscore-to-camel-case: true`가 처리한다. 단, 단일 설정 도메인인 `store`·`store_business_hour`·`store_holiday`는 기존 코드 호환을 위해 단수형을 유지한다.
- **시간 컬럼**: `created_at`, `updated_at`을 `DATETIME(6)`으로 둔다.
  - 생성 시각은 DDL의 `DEFAULT CURRENT_TIMESTAMP(6)`로 DB가 채운다.
  - 수정 시각은 DDL의 `ON UPDATE CURRENT_TIMESTAMP(6)`에 위임한다. `UPDATE` 문에서 `updated_at`을 직접 세팅하지 않는다.
  - 자바 서비스 코드에서 시간 값을 직접 세팅하지 않는다.
- **enum**: DB 컬럼은 `VARCHAR`로 둔다. MyBatis 기본 핸들러가 enum을 `name()` 문자열로 저장·조회하므로 별도 설정이 필요 없다. `INT`(ordinal) 저장은 금지한다. 상태 컬럼의 상세 규칙은 아래 [상태값(status) 공통 규칙](#상태값status-공통-규칙)을 따른다.
- **소프트삭제**: 공통 규약으로 강제하지 않는다. 이력 보존이 필요한 테이블만 담당자가 판단해 도입한다.

## 엔티티

- 순수 POJO로 둔다. JPA 애너테이션을 붙이지 않는다.
- getter/setter는 Lombok `@Getter @Setter`로 생성한다. 손으로 작성하지 않는다.
- 상속(BaseEntity)은 사용하지 않는다. 필요한 시간 컬럼은 각 엔티티에 직접 선언한다.
- import 정렬: 표준 라이브러리 → 빈 줄 → 서드파티(lombok 등).

```java
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Member {
    private Long id;
    private String email;
    private LocalDateTime createdAt;
}
```

## DTO

DB 모델과 화면 모델을 분리해, 화면 검증 규칙이 영속 모델로 번지지 않게 한다.

- **form**: 화면 입력 전용. Bean Validation(`@NotBlank`, `@Size` 등)을 여기에만 붙인다. 단일 필드로 표현 못 하는 교차 검증은 `@AssertTrue` 메서드로 둔다. (store `StoreUpdateForm` 참고)
- **view**: 화면 출력 전용. 불변 `record`로 두고, 여러 테이블 조회 결과를 서비스에서 하나로 조합한다. (store `StoreView` 참고)
- entity를 컨트롤러/화면에 직접 노출하지 않는다.

## MyBatis 매퍼

- 매퍼 인터페이스에 `@Mapper`를 붙이고, SQL은 XML에 작성한다.
- **컬럼을 명시**한다. `SELECT *`를 쓰지 않는다.
- 모든 사용자 입력은 `#{}`로 바인딩한다. `${}`는 사용하지 않는다(SQL 인젝션 방지).
- 컬럼과 필드명이 다르거나 타입 변환이 필요하면 `resultMap`을 쓴다.
- 자동 생성 키는 `useGeneratedKeys="true" keyProperty="id"`로 받는다.
- 단건 조회 반환은 `Optional<T>`을 사용한다. (store `findStoreById`)

## 서비스 · 트랜잭션

- 조회 메서드는 `@Transactional(readOnly = true)`.
- 쓰기 메서드는 `@Transactional`. 여러 테이블에 걸친 쓰기는 한 트랜잭션으로 묶어 일부만 반영되는 상태를 막는다. (store `updateStore`)
- 업무 규칙 위반은 `throw new BusinessException(도메인ErrorCode)`로 알린다.
- 상태 전이 검증은 service가 소유한다(아래 상태 규칙 참고). DB `CHECK`는 허용 값 집합만 지킨다.

## 에러 처리

- 도메인마다 `com.cakeshop.global.error.ErrorCode`를 구현한 enum을 둔다. 각 항목은 `코드·메시지·HTTP 상태`를 가진다. (store `StoreErrorCode`)
- 코드 접두어는 도메인별로 구분한다(`STORE_001`, `MEMBER_001` …).
- 화면에서 바로 고칠 수 있는 업무 오류는 컨트롤러에서 `bindingResult.rejectValue(...)`로 해당 입력 필드에 돌려준다. (store `addHoliday`)
- 외부 연동 실패처럼 원인 예외가 있으면 `new BusinessException(도메인ErrorCode, cause)`로 감싼다. 화면에는 `ErrorCode`의 메시지가 나가고 원인 스택은 로그에만 남는다 — 외부 오류 문구를 사용자에게 그대로 노출하지 않는다.
- **화면(`@Controller`)은 `GlobalExceptionHandler`가 오류 페이지를, JSON(`@RestController`)은 `ApiExceptionHandler`가 `{code, message}`를 돌려준다. 둘 다 `global/error`에 있고 자동으로 적용되므로 새 JSON 컨트롤러에 전용 advice를 만들지 않는다.** 도메인 고유 문구가 꼭 필요할 때만 `@RestControllerAdvice(assignableTypes = ...)`로 그 예외 하나만 덧붙인다(chat의 업로드 상한이 유일한 예). 전용 advice가 없으면 fetch 호출자에게 HTML 오류 페이지가 나간다.

## 컨트롤러

- 폼 제출은 PRG(Post-Redirect-Get) 패턴을 따른다. 성공 후 `redirect:`로 이동한다.
- 검증 실패 시에는 리다이렉트하지 않고 사용자가 입력한 form을 그대로 재렌더한다.
- 성공 알림은 `redirectAttributes.addFlashAttribute("successMessage", ...)`, 오류 알림은 `"errorMessage"` 키로 전달한다. 이 두 키를 `fragments/common/alert.html`이 읽어 화면에 출력한다.

## 인증

- 세션 기반 인증을 사용한다(구현 완료).
- 현재 로그인 회원은 컨트롤러 파라미터에 `@AuthenticationPrincipal MemberDetails member`로 직접 받는다. 세션 키를 직접 읽거나 별도 커스텀 애너테이션을 만들지 않는다.
- **역할(role)** 값은 접두어 없이 `USER` / `ADMIN`으로 DB에 저장한다. `MemberDetailsService`가 권한 문자열로 변환할 때 `ROLE_` 접두어를 붙이므로, `hasRole('ADMIN')` / `sec:authorize="hasRole('ADMIN')"`가 그대로 동작한다. role 컬럼에 직접 `ROLE_`를 넣지 않는다.
- 인증 인프라인 `global/security/MemberDetailsService`는 로그인 자격을 구성하기 위해 `MemberMapper`를 직접 사용할 수 있다. 이는 `Controller → Service → Mapper` 도메인 계층 규칙의 명시적 예외이며, 일반 Controller나 다른 도메인 Service에는 확대하지 않는다.

## 도메인 간 연동

- 다른 도메인의 테이블을 직접 JOIN·조회하지 않는다. 각 도메인이 공개한 **Service 인터페이스**를 통해 필요한 정보를 받는다.
- 구현이 아직 없으면 인터페이스 시그니처만 먼저 합의하고, 사용하는 쪽은 stub으로 개발을 진행한다.
- 최소 공개 계약(1차 합의 대상):
  - 회원: `MemberService.findById(id)` → (id, 이름, 권한, 상태)
  - 상품: `ProductQueryService.getSalesInfo(id)` → (판매가능여부, 가격, 재고)

## 추후 확정 (본보기 도메인에서 시연)

아래는 store 도메인이 다루지 않아 별도 예시로 기준을 확정한다. 확정 전까지 임의 구현하지 않는다.

- **목록·페이징**: 무한스크롤 / 페이지 번호 중 택1 (미정). 정해지면 카운트 쿼리와 `LIMIT/OFFSET` 표준을 여기에 추가한다.
- **도메인 간 FK 연동**: order ↔ member ↔ product 연결 예시로 확정한다.
- **상태 enum 컬럼**: 표준 방식은 아래 [상태값(status) 공통 규칙](#상태값status-공통-규칙)에서 확정했다. `OrderStatus`·`PaymentStatus`가 모범 구현이며, 도메인별 미확정 값만 담당자가 채운다.

---

# 상태값(status) 공통 규칙

> 목적: 13개 도메인에 흩어진 모든 `status`(및 상태처럼 보이는 값)를 **한 방식으로 통일**한다.
> 상태값 관련 결정은 이 절을 정본으로 삼는다.

## status 하나를 다루는 표준 방식 (전 도메인 공통)

한 상태 컬럼은 **3가지 표현**을 가진다. 어디를 정본으로 두는지가 핵심이다.

| 표현 | 어디에 | 규칙 |
|---|---|---|
| **저장값 = enum 이름** (영문 UPPER_SNAKE) | DB `VARCHAR` + `CHECK IN(...)`, Java `enum` | **이게 정본.** MyBatis가 enum ↔ 문자열을 이름으로 자동 매핑한다. |
| **한글 라벨** (판매 중, 승인 대기 …) | 화면 | **절대 저장하지 않는다.** enum의 `label()` 또는 view 헬퍼로 매핑한다. 목업에 하드코딩된 한글 배지는 전부 enum-driven으로 교체한다. |
| **전이 규칙** | Java `enum`의 `canTransitionTo()` | DB `CHECK`는 "허용 값 집합"만 지킨다. **전이는 service에서 검증**한다. SQL에 전이를 넣지 않는다. |

### 핵심 원칙

- **저장값은 영문 enum 이름 하나로 통일.** 한글·숫자·코드값으로 저장하지 않는다.
- **한글은 화면에서만.** 저장값과 라벨을 섞으면 세 표현이 서로 어긋난다(drift). 라벨 매핑은 enum이나 view가 소유한다.
- **전이는 service에서.** DB는 값 집합만, 상태 머신은 Java가 소유한다.
- `OrderStatus`(7개 + 전이)·`PaymentStatus`(6개)가 **이미 이 형태의 모범답안**이다. 나머지 담당자는 이 두 enum을 그대로 복제해서 자기 도메인에 적용한다.

### DB 컬럼 작성 규칙 (전원 합의)

- 타입: **`VARCHAR(20) NOT NULL`** (긴 값이 필요하면 그 컬럼만 예외적으로 늘리고 이유를 주석으로 남긴다)
- 제약: **`CONSTRAINT chk_<table>_status CHECK (status IN ('A','B', ...))`** — `store` DDL의 `chk_...` 네이밍 관례 준수
- 기본값: 신규 행이 시작하는 상태를 `DEFAULT`로 지정 (예: `members` → `ACTIVE`, `member_coupons` → `AVAILABLE`)
- 컬럼명: 상태는 `status`, 종류는 `type` / `category` (아래 분류에 따름)

## status 자리 전수 인벤토리 (코드 + 목업 통합)

| 도메인.컬럼 | 담당 | 목업 표기 | 저장값(enum) | 상태 |
|---|---|---|---|---|
| `members.status` | 수민 | 정상 / 이용 제한 | **`ACTIVE / SUSPENDED / WITHDRAWN` (확정)** | ✅ 확정 (V5, 스펙 docs/specs/member.md) |
| `products.status` | 시은 | 판매 중 / 판매 중지 | **`ACTIVE / INACTIVE` (확정)** | ✅ 확정 (V6, 스펙 docs/specs/product.md) |
| `product_options.status` | 시은 | (표기 없음) | **`ACTIVE / INACTIVE` (확정)** | ✅ 확정 (V13, 스펙 docs/specs/order-custom.md — product 스펙이 order(수제) 차례로 넘긴 항목) |
| `orders.status` | 주환 | 결제완료/확인중/제작중/픽업대기/픽업완료/취소/반려 | **`OrderStatus` 7개 (확정)** | ✅ 코드 확정 |
| `payments.status` | 주환 | 결제 완료 / 결제 대기 | **`PaymentStatus` 6개 (확정)** | ✅ 코드 확정 |
| `payment_cancellations.status` | 주환 | 취소 요청·완료·거절 | **`REQUESTED / DONE / REJECTED` (확정)** | ✅ 확정 (V8, 스펙 docs/specs/order-payment.md) |
| `coupons.status` | 정후 | 발급 중 | **`ACTIVE / SUSPENDED / ENDED` (확정)** | ✅ 확정 (V15, 스펙 docs/specs/coupon.md) |
| `member_coupons.status` | 정후 | 사용 가능 / 사용 완료 | **`AVAILABLE / USED` (확정)** | ✅ 확정 (V15, 스펙 docs/specs/coupon.md — 만료는 `expires_at` 파생값이라 저장하지 않는다) |
| `posts.status` | 현규 | 정상 / 제재 | **`ACTIVE / DELETED / BLOCKED` (확정)** | ✅ 확정 (V2, 스펙 docs/specs/community.md — `PostStatus` enum) |
| `comments.status` | 현규 | (표기 없음) | **`ACTIVE / DELETED` (확정)** | ✅ 확정 (V2, 스펙 docs/specs/community.md — `CommentStatus` enum. 댓글에는 제재가 없어 posts와 달리 `BLOCKED`를 두지 않는다) |
| `post_reports.status` | 현규 | (신고 처리) | **`PENDING / ACCEPTED / REJECTED` (확정)** | ✅ 확정 (V2, 스펙 docs/specs/community.md). ⚠️ 유일하게 자바 enum 없이 문자열로 다루는 상태다 — `CommunityAdminService`가 라벨을 switch로 매핑한다 |
| `reviews.status` | 현규 | 숨김 | **`VISIBLE / HIDDEN` (확정)** | ✅ 확정 (V16, 스펙 docs/specs/review.md — 삭제는 상태가 아니라 행 제거다) |
| `chat_rooms.status` | 민정 | 상담 가능 / 상담 종료 | **`OPEN / CLOSED` (확정)** | ✅ 확정 (V9, 스펙 docs/specs/chat.md) |
| `notification_deliveries.status` | 민정 | (화면 표기 없음 — 내부 전달 이력) | **`REQUESTED / SENT / FAILED / ABANDONED` (확정)** | ✅ 확정 (V12, 스펙 docs/specs/notification.md) |
| `custom_order_quotes.status` | 주환 | 승인 대기 / 승인됨 / 거절됨 | **`SENT / ACCEPTED / SUPERSEDED` (확정)** | ✅ 확정 (V13, 스펙 docs/specs/order-custom.md). 주문 상태가 아니라 **견적 회차의 상태**다 — `orders.status`는 확정 7개 그대로 |
| `custom_order_payment_links.status` | 주환 | (화면 표기 없음 — 내부 링크 상태) | **`ISSUED / USED / EXPIRED / REVOKED` (확정)** | ✅ 확정 (V13, 스펙 docs/specs/order-custom.md) |
| (statistics — status 자리 없음) | 현규 | 일별 / 주별 / 월별 | **`StatsPeriod`는 status가 아니다** | ✅ 확정 (스펙 docs/specs/statistics.md — 집계 단위는 DB에 저장하지 않는 조회 파라미터이고 지표는 전부 파생값이라 statistics는 테이블도 status도 소유하지 않는다. 여러 도메인이 공유하므로 `global/common/stats`에 둔다) |

### 이미 확정된 두 enum

**`OrderStatus` (일반·수제 케이크 공통, 7개 + 전이규칙 — 코드에 확정됨)**

```
정상 흐름:
일반 케이크: PAID(결제 완료) → READY_FOR_PICKUP(픽업 대기) → PICKED_UP(픽업 완료)
수제 케이크: UNDER_REVIEW(확인 중) → IN_PRODUCTION(제작 중) → READY_FOR_PICKUP → PICKED_UP
예외 흐름:
UNDER_REVIEW → REJECTED               (수제 반려, 최종)
* 최종 전 모든 상태 → CANCELED         (주문 취소, 최종)
최종 상태: PICKED_UP / CANCELED / REJECTED
```
전이 규칙은 `OrderStatus.canTransitionTo()`가 소유한다. SQL의 `CHECK`는 7개 값 집합만 나열한다(`docs/sql/V3_order_status.sql`).

**`PaymentStatus` (토스 결제 상태, 6개 — 코드에 확정됨. 주문 enum과 절대 섞지 않는다)**

```
READY / DONE / CANCELED / PARTIAL_CANCELED / ABORTED / EXPIRED
```

## ⚠️ status처럼 보이지만 status가 아닌 것 (분류 필수)

목업 배지를 그대로 컬럼으로 만들지 않는다. 4종류로 갈린다.

### 불리언 → enum 만들지 않는다

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 알림 `읽음 / 미읽음` | `notifications.is_read BOOLEAN` | 민정 |
| 영업시간 `휴무` | `store_business_hour.is_closed`(이미 존재) | — |

### 파생값 → 저장하지 않고 계산한다

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 상품 `품절 / 재고 부족` | `stock_quantity`에서 계산(0이면 품절). **`products.status`(판매 on/off)와 별개** | 시은 |
| 채팅 `미답변` | 마지막 일반 메시지(`TEXT`/`IMAGE`) 발신자가 `CUSTOMER`인지로 파생. `SYSTEM_CARD`는 영향을 주지 않으며 방 자체 `status`와 다름 | 민정 |

> 시은 주의: `products.status`(ACTIVE/INACTIVE = 판매 스위치)와 재고(품절/재고부족)는 **다른 축**이다. 하나의 컬럼으로 합치지 않는다.

### 종류(type/category) → status가 아니다 (단, 저장 방식은 동일 패턴)

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 커뮤니티 글 `후기 / 질문 / 레시피 / 자유` | `posts.category` (`REVIEW/QUESTION/RECIPE/FREE`) — status와 별도 컬럼 | 현규 |
| 알림 `주문 승인 / 결제 완료 …` | `NotificationType` enum 13개 확정 — 고객 `CHAT_MESSAGE`/`ORDER_PAID`/`ORDER_IN_PRODUCTION`/`ORDER_READY_FOR_PICKUP`/`ORDER_PICKED_UP`/`ORDER_CANCELED`/`ORDER_REJECTED`/`CUSTOM_ORDER_QUOTE`/`PAYMENT_REQUESTED`/`REVIEW_REPLY`(V19), 관리자 `ADMIN_CHAT_MESSAGE`/`ADMIN_ORDER_PLACED`/`ADMIN_ORDER_CANCELED`. status와 별도 컬럼(`notification_type`) | 민정 |
| 알림 전달 경로 | `notification_deliveries.channel` (`DeliveryChannel` — 현재 `WEBSOCKET` 하나). 같은 테이블의 `status`(REQUESTED/SENT/FAILED/ABANDONED)와 별도 컬럼 | 민정 |

`type`/`category`도 저장값·라벨 규칙은 status와 동일하게 적용한다(영문 enum 이름 저장, 한글 라벨 미저장).

### 다른 도메인의 status를 빌려 표시하는 것 → 자기 컬럼이 아니다

| 목업 표기 | 실제 |
|---|---|
| 채팅 목록의 `승인 대기 / 제작 중` 배지 | 연결된 **주문의 `orders.status`**를 표시한 것. `chat_rooms`에 저장하지 않는다 |

## 지금 못 박을 것 vs 담당자가 채울 것

### 지금 전원 합의로 확정 (미루면 서로 깨진다)

1. **표준 방식** — enum 이름 저장 / 한글 라벨 미저장 / 전이는 service. `OrderStatus`·`PaymentStatus`가 레퍼런스.
2. **DB 컬럼 규칙** — `VARCHAR(20) NOT NULL` + `chk_<table>_status CHECK IN(...)` + 시작 상태 `DEFAULT`.
3. **함정 분류** — "재고·읽음·글종류·남의 status는 내 status 컬럼이 아니다"를 합의.

### 담당자가 자기 DDL 짤 때 채우던 ☐ — 전부 확정 완료

| 담당 | 채운 결과 |
|---|---|
| 시은 | `product_options.status` = `ACTIVE / INACTIVE` (V13 — product 스펙이 order(수제) 차례로 넘겼던 항목) |
| 주환 | `payment_cancellations.status` = `REQUESTED / DONE / REJECTED` (V8) |
| 정후 | `coupons.status` = `ACTIVE / SUSPENDED / ENDED`, `member_coupons.status` = `AVAILABLE / USED` (V15) |
| 현규 | `comments.status` = `ACTIVE / DELETED`, `post_reports.status` = `PENDING / ACCEPTED / REJECTED` (V2), `reviews.status` = `VISIBLE / HIDDEN` (V16) |
| 민정 | `chat_rooms.status` = `OPEN / CLOSED` (V9), `notification_deliveries.status` 4개 (V12), `NotificationType` 13개 (`docs/specs/notification.md`) |

> 새 상태 컬럼을 추가할 때는 위 인벤토리에 행을 추가하고 enum + DDL을 함께 커밋한다.

## 한 줄 요약

**저장값은 영문 enum 이름 하나로 통일, 한글은 화면에서만, 전이는 service에서. 그리고 재고·읽음·글종류·남의 도메인 status는 내 status 컬럼이 아니다.**
