# cakeshop 프로젝트 전체 분석 피드백 v1

- 분석일: 2026-07-28
- 대상 브랜치: `dev`
- 기술 구성: Spring Boot 4.0.2, Java 21, Gradle, Thymeleaf, MyBatis, MariaDB

## 종합 결론

현재 프로젝트는 기능 구현, 문서, 테스트 밀도가 상당히 높은 Spring Boot 모놀리식 애플리케이션이다. 개발 및 포트폴리오 기준으로는 완성도가 높지만, 실서비스 배포 기준에서는 결제 웹훅과 파일 접근 제어를 먼저 보완해야 한다.

전체 빌드는 성공했고, 82개 테스트 스위트의 488개 테스트가 모두 통과했다. 분석 종료 시점의 Git 작업 트리도 깨끗하다.

## 우선순위별 핵심 피드백

### P0 — 결제 웹훅을 신뢰하고 상태를 직접 변경함

가장 먼저 수정해야 할 부분이다.

현재 웹훅 컨트롤러는 요청 본문의 `status`, `orderId`, `paymentKey`를 그대로 저장하고, 처리기는 이 값을 제공자 재조회 없이 결제 상태에 반영한다.

- [`TossWebhookController.java`](src/main/java/com/cakeshop/domain/payment/controller/TossWebhookController.java)
- [`WebhookEventProcessor.java`](src/main/java/com/cakeshop/domain/payment/service/WebhookEventProcessor.java)

문제는 다음과 같다.

- 일반 결제 웹훅은 서명 헤더가 없으므로 결제 조회 API로 상태를 재검증해야 한다.
- 실제 전송 ID는 `tosspayments-webhook-transmission-id` 헤더인데, 현재 코드는 본문의 `eventId`를 찾고 없으면 매번 UUID를 만든다. 따라서 재전송 중복 제거가 제대로 작동하지 않는다.
- 외부 취소 웹훅은 `payments.status`만 변경한다. 주문 취소, 재고 복구, 쿠폰 복원, 취소 이력 생성은 수행되지 않아 내부 정합성이 깨진다. 정상 취소 경로인 [`RefundProcessor.java`](src/main/java/com/cakeshop/domain/payment/service/RefundProcessor.java)와 동작이 다르다.

토스 공식 문서도 일반 결제 웹훅은 조회 API로 재검증하도록 안내하며, 전송 ID가 헤더에 있다고 명시한다.

- [토스 웹훅 이벤트 문서](https://docs.tosspayments.com/reference/using-api/webhook-events)
- [토스 연동 Quick Reference](https://docs.tosspayments.com/guides/v2/get-started/llms-quick-reference)

권장 수정 순서는 다음과 같다.

1. 전송 ID 헤더와 원본 payload만 먼저 저장한다.
2. 처리 시 `paymentKey`로 토스 결제 조회 API를 호출한다.
3. 조회 결과의 주문번호, 금액, 상태를 내부 데이터와 대조한다.
4. 외부 취소는 `RefundProcessor`와 같은 단일 정합성 트랜잭션으로 반영한다.
5. 검증되지 않은 `providerStatus`로 DB를 직접 변경하지 않는다.

### P1 — 결제 실패 콜백의 소유권 검증 누락

[`PaymentController.java`](src/main/java/com/cakeshop/domain/payment/controller/PaymentController.java)의 GET 실패 콜백은 전달받은 `orderId`만으로 READY 결제를 `ABORTED` 처리한다. [`PaymentFacade.java`](src/main/java/com/cakeshop/domain/payment/service/PaymentFacade.java)에도 회원이나 체크아웃 소유권 검증이 없다.

주문번호가 UUID 기반이라 추측 난도는 높지만, 이것은 접근 제어를 대체하지 못한다. 실패 URL은 브라우저가 전달하는 값이므로 신뢰해서는 안 된다.

`memberId + session CheckoutDraft + idempotencyKey`를 모두 대조한 경우에만 상태를 변경하는 것이 안전하다. 주문제작 실패 콜백도 토큰뿐 아니라 현재 회원 소유권을 함께 확인해야 한다.

### P1 — 주문제작 참고 이미지가 공개 리소스임

주문제작 고객 이미지는 공용 저장소에 기록된다.

- 저장: [`CustomOrderService.java`](src/main/java/com/cakeshop/domain/order/service/CustomOrderService.java)
- 전체 공개: [`SecurityConfig.java`](src/main/java/com/cakeshop/global/security/SecurityConfig.java)
- 디렉터리 공개 서빙: [`WebConfig.java`](src/main/java/com/cakeshop/global/config/WebConfig.java)

URL이 UUID라 쉽게 추측되지는 않지만, 고객 도안이나 인물 사진 같은 민감 자료가 인증 없이 열리는 구조다.

이미 구현된 채팅 이미지 패턴처럼 비공개 디렉터리에 저장하고, `주문 소유자 또는 관리자` 검증 후 컨트롤러가 스트리밍하는 방식이 적합하다.

### P1 — 이용 제한된 회원의 기존 세션이 유지됨

회원 상태는 로그인할 때만 확인한다.

- 로그인 시 상태 확인: [`MemberDetailsService.java`](src/main/java/com/cakeshop/global/security/MemberDetailsService.java)
- 관리자 이용 제한: [`MemberAdminService.java`](src/main/java/com/cakeshop/domain/member/service/MemberAdminService.java)

이미 로그인한 사용자는 관리자가 계정을 정지해도 기존 세션으로 계속 요청할 수 있다. 단일 인스턴스라면 `SessionRegistry`로 해당 회원 세션을 만료시키고, 다중 인스턴스라면 Spring Session 기반으로 확장하는 것이 좋다.

- [Spring Security 세션 관리 문서](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html)

### P1 — 운영 배포 안전장치 부족

현재 설정은 개발 환경으로는 편하지만 운영에서 실수하기 쉽다.

- 기본 프로필이 `local`이다.
- 결제 제공자 기본값이 `mock`이다.
- RDS 연결이 `sslMode=trust`를 사용한다.
- RDS 프로필에서 Flyway가 비활성화되어 있다.

관련 설정은 [`application.yml`](src/main/resources/application.yml)에 있다.

운영 전에는 별도 `prod` 프로필을 만들어 다음을 강제하는 것을 권장한다.

- `PAYMENT_PROVIDER=toss`가 아니면 부팅 실패
- 시크릿 키 누락 시 부팅 실패
- RDS CA와 `sslMode=verify-full` 사용
- 마이그레이션을 배포 파이프라인의 명시적 단계로 실행
- 로그인, 회원가입, 웹훅 rate limit 적용

### P2 — 웹훅 트랜잭션이 실제로 적용되지 않음

`WebhookEventProcessor.processPending()`이 같은 클래스의 `@Transactional process()`를 직접 호출한다.

Spring 기본 프록시 방식에서는 자기 호출에 `@Transactional`이 적용되지 않는다.

- [`WebhookEventProcessor.java`](src/main/java/com/cakeshop/domain/payment/service/WebhookEventProcessor.java)
- [Spring Framework 프록시 문서](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

한 건 처리기를 별도 Bean으로 분리하거나 `TransactionTemplate`을 사용해야 결제 상태 변경과 이벤트 처리 완료 표시가 원자적으로 묶인다.

### P2 — 서비스 비대화와 도메인 순환 의존

프로덕션 Java 코드는 약 14,409줄이고 다음 클래스가 특히 크다.

| 클래스 | 줄 수 |
|---|---:|
| `CustomOrderService` | 601 |
| `ChatService` | 505 |
| `StoreService` | 400 |
| `OrderService` | 342 |
| `ReviewService` | 333 |

또한 `order ↔ payment`, `order ↔ cart`, `product ↔ review`, `member ↔ order` 형태의 의존 순환이 있다. 지금은 동작하지만 기능 추가 시 수정 범위와 테스트 비용이 빠르게 커질 수 있다.

대규모 재작성보다는 다음 단위부터 점진적으로 분리하는 것이 좋다.

- 조회 전용 Query Service
- 명확한 유스케이스별 Command Service
- 결제와 주문 사이 Port 인터페이스
- 커밋 이후 처리 가능한 기능의 도메인 이벤트화

목업 호환을 위한 무인자 컨트롤러와 `service == null` 분기도 제거하면 생성자 주입 불변성과 테스트 신뢰도가 좋아진다.

### P2 — 자동 품질 게이트 보강 필요

테스트 수와 범위는 강점이지만 현재 빌드에는 다음 자동 검증이 없다.

- JaCoCo 커버리지 측정과 최소 기준
- Checkstyle 또는 Spotless
- SpotBugs 또는 정적 분석
- 의존성 취약점 검사
- 실제 토스 API 계약 테스트

특히 현재 일부 테스트는 웹훅 원문 상태를 직접 반영하거나 실패 콜백이 소유권 없이 결제를 중단하는 현재 동작을 정상 동작으로 고정한다. 테스트 통과 여부와 업무·보안적으로 안전한 동작은 구분해야 한다.

### P3 — Gradle Wrapper 무결성 보강

[`gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties)에 `distributionSha256Sum`이 없다. 빌드 공급망 무결성을 높이려면 사용 중인 Gradle 배포본의 공식 SHA-256 값을 고정하는 것이 좋다.

## 잘된 점

- 전체 테스트 488개와 실제 흐름을 다루는 E2E 테스트가 존재한다.
- CI에서 빈 MariaDB로 Flyway 마이그레이션 재생성을 검증한다.
- MyBatis에서 사용자 입력에 `${}` 치환을 사용하지 않는다.
- 재고 조건부 차감, 결제 상태 조건부 UPDATE, 행 잠금 등 동시성 방어가 있다.
- 결제 외부 호출을 DB 트랜잭션 밖에 두고 보상 취소를 구현했다.
- CSRF를 전체 비활성화하지 않고 웹훅만 제외했다.
- 이미지 매직 바이트와 저장 경로 탈출을 검증한다.
- 채팅 이미지는 소유권 검증 후 비공개로 제공한다.
- 오류 코드와 HTML/JSON 예외 처리가 분리되어 있다.
- 업무 규칙, DB, 도메인 스펙, 온보딩 문서가 상세하다.
- 로컬 개발 시드와 공용 RDS 스키마 데이터를 분리했다.

## 구조 평가

현재 구조는 도메인별 수직 슬라이스를 따르는 모듈형 모놀리스에 가깝다.

```text
Controller
    ↓
Service / Use Case
    ↓
Mapper Interface
    ↓
MyBatis XML
    ↓
MariaDB
```

기본적인 계층 책임은 잘 지켜지고 있고 컨트롤러에서 Mapper를 직접 호출하는 구조도 보이지 않는다. 다만 기능이 커지면서 일부 Service가 여러 유스케이스, 상태 전이, View 조합을 동시에 책임지기 시작했다.

다음 단계에서는 패키지 수를 늘리는 것보다 큰 Service의 변경 이유를 분리하는 것이 효과적이다. 예를 들어 주문제작은 요청서 생성, 견적 수락, 결제 링크, 상세 조회를 각각 독립적인 유스케이스로 분리할 수 있다.

## 테스트 및 빌드 검증 결과

### 실행 결과

- `gradlew test --no-daemon`: 성공
- `gradlew build --no-daemon`: 성공
- 테스트 스위트: 82개
- 테스트: 488건
- 실패: 0건
- 오류: 0건
- 스킵: 0건
- 실행 가능한 Spring Boot JAR 생성 성공
- 분석 종료 시 Git 작업 트리 변경 없음

### 검증 범위의 한계

- 테스트에서는 결제 제공자가 `mock`으로 고정되므로 실제 토스 호출은 검증하지 않는다.
- 로컬과 CI의 MariaDB 마이그레이션은 검증하지만 실제 RDS 인증서와 네트워크 조건은 검증하지 않는다.
- 의존성 취약점과 정적 분석 결과는 현재 빌드에 포함되지 않는다.
- 다중 애플리케이션 인스턴스 환경의 스케줄러 중복 실행은 검증하지 않는다.

## 권장 개선 순서

1. 웹훅 전송 ID 수신 및 토스 조회 API 재검증
2. 웹훅 취소를 주문·재고·쿠폰·취소 이력과 하나의 정합성 흐름으로 통합
3. 일반 결제 및 주문제작 실패 콜백 소유권 검증
4. 주문제작 참고 이미지 비공개화
5. 관리자 회원 정지 시 기존 세션 만료
6. 운영 전용 프로필과 fail-fast 설정 추가
7. `WebhookEventProcessor` 자기 호출 트랜잭션 수정
8. 대형 Service를 유스케이스 단위로 점진 분리
9. 커버리지, 정적 분석, 취약점 검사 품질 게이트 추가
10. Gradle Wrapper SHA-256 고정

## 최종 평가

이 프로젝트의 가장 큰 강점은 많은 기능을 실제 DB 흐름으로 끝까지 연결하면서도 테스트와 문서를 함께 유지했다는 점이다. 단순 CRUD 예제를 넘어 결제 보상, 쿠폰 복구, 재고 동시성, 알림 전달 이력, 비공개 채팅 이미지 같은 운영 관점의 고민도 코드에 반영되어 있다.

현재 단계에서 기능을 더 추가하기보다는 결제 웹훅 신뢰 경계와 고객 이미지 접근 경계를 먼저 닫는 것이 투자 대비 효과가 가장 크다. 이 두 영역을 보완하면 실서비스 준비 수준이 크게 올라간다.
