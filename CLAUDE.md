# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spring Boot 4.0.2 · Java 21 · Gradle · Thymeleaf(SSR) · MyBatis · MariaDB 케이크샵 팀 프로젝트. Docker는 사용하지 않는다. DB 스키마는 Flyway로 관리한다.

**작업 목적**: 팀원 간 역할 분담은 정해져 있지만(4장 참조), 이 저장소에서는 현규가 **전 도메인을 우선 완성해 팀원들에게 표준 예시로 제공**하는 것을 목표로 한다. 따라서 담당 도메인(community·review) 밖의 작업도 정상이며, 모든 구현은 다른 팀원이 그대로 따라할 수 있도록 store 표준 슬라이스와 코딩 컨벤션(5장)을 충실히 지켜 "예시 품질"로 작성한다.

## 1. 절대규칙

위반하면 안 되는 규칙. 아래 어떤 항목과 충돌하는 요청이 있으면 먼저 확인을 구한다.

- 커밋 메시지는 **한글**로 작성한다.
- `main`에 직접 push하지 않는다. 브랜치는 `main`(안정) ← `dev`(통합) ← `feature/*`(작업), 병합은 PR로만 한다.
- `.env`·비밀값·실제 개인정보를 커밋하지 않는다. repo에는 `.env_sample`만 둔다.
- SQL 바인딩은 `#{}`만 사용한다. `${}`는 금지 (SQL 인젝션).
- JPA를 쓰지 않는다. 엔티티는 순수 POJO이며 저장은 MyBatis mapper 호출로만 한다.
- 상태값(status)은 영문 enum 이름(UPPER_SNAKE)으로만 저장한다. 한글 라벨은 절대 저장하지 않고 화면에서만 매핑하며, 상태 전이 검증은 service가 소유한다.
- 다른 도메인의 테이블을 직접 JOIN하거나 다른 도메인의 Mapper를 호출하지 않는다. 상대 도메인이 공개한 Service 인터페이스로만 연동한다.
- `src/main/resources/db/migration`의 마이그레이션 파일은 **한 번 커밋되면 수정하지 않는다**. 스키마 변경은 항상 새 `V<다음번호>__<설명>.sql`(언더바 2개)을 추가한다. Flyway가 체크섬을 검증하므로 수정하면 이미 적용한 팀원의 DB에서 부팅이 실패한다. 소급 반영할 "정본"은 더 이상 없다.
- 개발용 더미 시드는 `src/main/resources/db/seed/R__dev_seed.sql`에만 둔다(`local` 프로필 전용). 운영에도 필요한 코드값은 `db/migration`의 V파일에 넣는다 — 공용 RDS에 샘플 데이터가 들어가면 안 된다.
- 공용 RDS는 Flyway 자동 실행이 꺼져 있다(`rds` 프로필 `spring.flyway.enabled: false`). 스키마 반영은 담당자가 의도적으로 한 번만 켜서 수행한다. 임의로 켜지 않는다. 자세한 내용은 `docs/database.md`.
- `created_at`/`updated_at`을 자바 코드나 UPDATE 문에서 직접 세팅하지 않는다. DDL의 `DEFAULT`/`ON UPDATE CURRENT_TIMESTAMP(6)`에 위임한다.
- `global/*`·`store`·`home` 공통 코드는 팀 합의(PR) 없이 변경하지 않는다.
- 도메인 구현에 착수하기 전에 `docs/specs/<도메인>.md` 스펙을 먼저 작성·확정한다(`/new-domain` 절차, 템플릿 `docs/specs/_template.md`). frontmatter가 `status: approved`가 아닌 도메인의 운영 코드 생성·수정은 훅이 차단한다(`home` 조합 계층 제외).
- 기능 작업은 **PR 생성까지만** 한다. 머지는 사람이 PR을 검토하고 지시했을 때만 하며, 머지했으면 **머지된 dev에서 검증까지 한 세트로** 끝낸다(`/merge-feature` 절차). 머지를 감지하면 검증 게이트가 무장되고, 미검증 종료를 최대 3회 차단한다. 상한을 넘긴 종료도 `ESCALATED`로 기록되며 pending 상태는 검증 또는 사유 있는 `-Skip` 전까지 유지된다.

## 2. 아키텍처

### 패키지 구조

```
com.cakeshop
├── global/          공통 기반 (전 도메인 공유 — 변경 시 PR 합의 필요)
│   ├── config/      MyBatis·Web·Scheduling 설정
│   ├── security/    세션 기반 인증, MemberDetails(Service), SecurityConfig
│   ├── error/       ErrorCode 인터페이스, BusinessException, GlobalExceptionHandler
│   ├── common/paging/  PageRequest, PageResult
│   └── infra/       FileStorageClient (로컬 파일 저장, /uploads/** 서빙)
└── domain/{13개}/   각 도메인 동일 구조:
    controller/ service/ mapper/ entity/ dto/form/ dto/view/ error/
```

- MyBatis SQL은 전부 XML: `src/main/resources/mapper/<도메인>/XxxMapper.xml`
- 계층 호출은 `Controller → Service → Mapper` 단방향. 계층 건너뛰기·역방향 호출 금지.
- `home`은 전용 Mapper 없이 각 도메인의 공개 View를 조합만 하는 얇은 계층.

### 표준 구현 예시

- **`domain/store`가 팀 표준 수직 슬라이스** — 계층 분리, 트랜잭션, 검증 실패 재렌더, FlashMessage까지 판단이 서지 않으면 store 코드를 그대로 따른다. 코드 흐름 해설은 `docs/store-usecase-flow.md`(스냅샷 문서) 참고.
- 목록·페이징 포함 전체 CRUD는 현재 브랜치에 product 실구현이 병합된 경우에만 `domain/product`를 참조한다. 병합 전 브랜치에서는 `TodoList.md`와 실제 코드를 우선한다.

### 화면(템플릿)

규격 정본은 `docs/conventions.md` 2부. 고객 화면은 `fragments/common/head·header·footer`, 관리자 화면은 `fragments/admin/sidebar·header` 프래그먼트를 재사용하고 화면 고유 마크업만 작성한다. 공통 스타일·스크립트는 `static/css/app.css`·`static/js/app.js`에만 둔다. `successMessage`/`errorMessage`는 `fragments/common/alert.html`이 출력한다.

목업 화면에 백엔드를 붙일 때 URL과 템플릿은 유지하고 Controller의 Model 데이터와 비활성화된 버튼만 교체한다. `scripts\import-customer-mockups.ps1`은 `$screenMap`에 남아 있는 목업만 덮어쓴다. 고객 화면은 전부 실구현으로 전환돼 현재 맵은 비어 있으며(스크립트는 CSS·JS만 갱신), 새 목업을 이관할 때만 항목을 추가하고 실구현 전환 시 다시 제외한다.

## 3. 빌드/테스트

```powershell
# 로컬 DB로 실행 (개인 개발 표준이자 기본 프로필)
.\gradlew.bat bootRun --args="--spring.profiles.active=local"

# 빌드
.\gradlew.bat build

# 전체 테스트
.\gradlew.bat test

# 단일 테스트 클래스 / 메서드
.\gradlew.bat test --tests "com.cakeshop.domain.store.service.StoreServiceTests"
.\gradlew.bat test --tests "com.cakeshop.domain.store.service.StoreServiceTests.메서드명"
```

- DB 접속값은 프로젝트 루트 `.env`에서 읽는다(`LOCAL_DB_*`, `RDS_*`).
- 실행 중 프로필 전환 불가 — 서버를 `Ctrl+C`로 종료 후 재실행한다(안 하면 `Port 8080 was already in use`).
- 실행 후 `http://localhost:8080/screens`에서 전체 화면 목록 확인. 관리자 화면은 `admin@cakeshop.local / Admin1234!`(V1 시드 계정)로 로그인.
- **DB 스키마**: Flyway가 관리한다. 빈 DB(`CREATE DATABASE cakeshop`)만 만들어 두면 `bootRun` 시 `db/migration`이 자동 적용된다(`local`은 `db/seed`의 샘플까지). 수동 적용은 하지 않는다. 변경 규칙은 절대규칙, 상세는 `docs/database.md` 참조.

## 4. 도메인 컨텍스트

### 팀 분담

도메인 = `feature/*` 브랜치 단위. 단, 이 표는 최종 오너십 기준이고 현재 작업 방식은 문서 상단의 **작업 목적**을 따른다(현규가 전 도메인을 예시로 우선 완성).

| 담당 | 도메인 |
|---|---|
| 수민 | member, cart |
| 주환 | order, payment |
| 정후 | coupon |
| 민정 | chat, notification |
| 현규 | community, review |
| 시은 | product |
| 공통(PR 합의 필요) | store, global/*, home |
| 후반 별도 | statistics |

⚠️ 결제 흐름은 3인 공유: `cart`(수민) → `order`·`payment`(주환) ← `coupon`(정후)이 상태 전이와 checkout 흐름을 공유하므로 인터페이스 변경 시 셋이 먼저 합의한다.

### 확정된 상태 enum (값 정본: docs/conventions.md 인벤토리)

- **`OrderStatus` 7개** — 일반·수제 케이크 공통, 전이는 `canTransitionTo()`가 소유:
  - 일반: `PAID → READY_FOR_PICKUP → PICKED_UP`
  - 수제: `UNDER_REVIEW → IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP`, 반려 `UNDER_REVIEW → REJECTED`
  - 최종 상태: `PICKED_UP` / `CANCELED` / `REJECTED`. 시작 상태가 유형별로 달라 DB DEFAULT 없음(서비스가 세팅).
- **`PaymentStatus` 6개** — 토스 결제 상태. 주문 enum과 절대 섞지 않는다.
- **community·review 4개 테이블 확정** — posts(`ACTIVE/DELETED/BLOCKED`), comments(`ACTIVE/DELETED`), post_reports(`PENDING/ACCEPTED/REJECTED`), reviews(`VISIBLE/HIDDEN`).
- 함정 분류: 읽음 여부는 BOOLEAN, 품절·미답변은 파생값(저장 금지), 글 종류는 `category`/`type` 별도 컬럼, 다른 도메인의 status는 내 컬럼이 아니다.

### 현재 구현 상태

개발 순서 정본은 루트의 `TodoList.md`(의존 관계 기준 5단계 + 후속) — 다음 작업은 여기서 고른다. **13개 도메인이 모두 실구현이며 목업 화면은 남아 있지 않다**(각 스펙은 `docs/specs/`). 조합 계층이 둘인데, `home`은 store·product의 공개 View를, `statistics`는 각 도메인의 집계 View를 조합한다 — 둘 다 자기 테이블도 Mapper도 없다. 결제는 토스 실결제까지 붙어 있고 제공자는 `cakeshop.payment.provider`로 고른다(`mock` 기본 / `toss`). 화면별 현황 표는 README 참조. 화면 렌더링 스모크는 도메인별 전용 테스트(`StoreAdminControllerTests`·`*ScreenRenderingTests` 패턴)로 둔다.

## 5. 코딩 컨벤션

정본은 `docs/conventions.md`(1부 백엔드 · 2부 화면 템플릿 · 3부 상태값). 도메인을 가로지르는 업무 규칙은 `docs/business-rules.md`. 핵심 요약:

- **entity**: 순수 POJO + Lombok `@Getter @Setter`. BaseEntity 상속 금지. 시간 컬럼은 각 엔티티에 직접 선언.
- **dto/form**: 화면 입력 + Bean Validation(검증은 여기에만, 교차 검증은 `@AssertTrue`). **dto/view**: 불변 `record`, 화면 출력 전용. entity를 화면에 직접 노출하지 않는다.
- **Mapper XML**: 컬럼 명시(`SELECT *` 금지), 단건 조회는 `Optional<T>`, 생성 키는 `useGeneratedKeys="true" keyProperty="id"`. camelCase 매핑은 `map-underscore-to-camel-case`가 처리.
- **Service**: 조회 `@Transactional(readOnly = true)`, 쓰기 `@Transactional`(여러 테이블 쓰기는 한 트랜잭션). 업무 규칙 위반은 `throw new BusinessException(도메인ErrorCode)`.
- **에러**: 도메인마다 `ErrorCode` 구현 enum(`STORE_001` 식 접두어). 화면에서 고칠 수 있는 오류는 `bindingResult.rejectValue(...)`로 필드에 반환.
- **Controller**: PRG 패턴. 성공 시 `redirect:` + `redirectAttributes.addFlashAttribute("successMessage", ...)`(오류는 `"errorMessage"` — 문자열 키가 표준, FlashMessage 상수 클래스 없음), 검증 실패 시 리다이렉트 없이 form 재렌더.
- **인증**: 로그인 회원은 `@AuthenticationPrincipal MemberDetails member`로 받는다. role은 DB에 `USER`/`ADMIN`으로 저장(`ROLE_` 접두어는 MemberDetailsService가 붙인다).
- 인증 인프라인 `global/security/MemberDetailsService → MemberMapper` 직접 호출만 계층 규칙의 예외다. 일반 Controller나 다른 도메인 Service로 확대하지 않는다.
- **DB 네이밍**: 테이블·컬럼 `snake_case`, 테이블명 복수형(예외: `store`·`store_business_hour`·`store_holiday`는 단수). PK는 `BIGINT AUTO_INCREMENT id`(Java `Long`). 상태 컬럼은 `VARCHAR(20) NOT NULL` + `chk_<table>_status CHECK` + 시작 상태 `DEFAULT`.
