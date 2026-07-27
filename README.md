# cakeshop

Spring Boot 4.0.2 · Java 21 · Gradle · Thymeleaf · MyBatis · MariaDB

[![CI](https://github.com/HyunGyu-Cho/cakeRealPractice/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/HyunGyu-Cho/cakeRealPractice/actions/workflows/ci.yml)

## 개발 환경

각 개발자가 PC에 MariaDB를 직접 설치하고 Spring Boot를 실행한다. 기본적으로 각자의 로컬 MariaDB를 사용하고, 필요할 때만 `rds` 프로필로 공용 AWS RDS에 접속한다. Docker는 사용하지 않는다.

## CI (PR 자동 검증)

`dev`·`main`으로 향하는 PR과 push마다 GitHub Actions가 다음을 검증한다(`.github/workflows/ci.yml`).

1. **마이그레이션 재생성** — 빈 MariaDB 컨테이너에 앱을 띄우면 Flyway가 `db/migration` 전체를 처음부터 순서대로 적용한다. 마이그레이션이 빠졌거나 순서·의존이 어긋나면 여기서 걸린다.
2. **빌드·전체 테스트** — 위에서 만든 DB를 대상으로 `./gradlew build`를 실행한다.
3. **부트 jar 보관** — `dev`·`main` push일 때 실행 가능한 jar를 Actions 아티팩트로 남긴다.

CI의 DB는 잡마다 새로 뜨고 끝나면 버려지는 일회용 컨테이너다. 공용 RDS나 각자의 로컬 MariaDB는 건드리지 않는다.

**로컬은 통과하는데 CI만 실패한다면** 자기 로컬 DB가 마이그레이션 결과와 어긋났을 가능성이 높다(예전에 손으로 고쳤거나, 베이스라인 이전의 흔적이 남았거나). 빈 DB를 하나 만들어 거기로 `bootRun` 해보면 구분된다.

## 로컬 DB 준비

1. 각 PC에 MariaDB 11.4를 설치하고 실행한다.
2. `cakeshop` 데이터베이스와 접속 계정을 생성한다.
3. `.env_sample`을 `.env`로 복사하고 `LOCAL_DB_*` 값을 자신의 MariaDB에 맞춘다.
4. **SQL은 손으로 적용하지 않는다.** 빈 데이터베이스 상태로 `bootRun` 하면 Flyway가 스키마와 시드를 자동 구성한다.

   이미 예전 V파일로 세팅해둔 DB도 그대로 쓰면 된다. 첫 실행에서 Flyway가 `flyway_schema_history` 테이블을 만들고 "베이스라인(V1)까지 적용됨"으로 기록한 뒤 그 이후 것만 적용한다. 자세한 규칙은 [`docs/sql/README.md`](docs/sql/README.md).

## 실행 프로필 선택

애플리케이션을 시작할 때 `local`과 `rds` 중 하나를 선택한다. 기본 프로필은 `local`이며, 공용 AWS RDS는 `rds`를 명시한 경우에만 접속한다. 실행 중에는 프로필을 바꿀 수 없으므로 전환하기 전에 실행 중인 서버를 `Ctrl+C`로 종료해야 한다. 종료하지 않고 다시 실행하면 `Port 8080 was already in use` 오류가 발생한다.

### 로컬 DB로 실행

개인 개발과 화면 확인에는 `local` 프로필을 사용한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

`local`이 기본 프로필이므로 `.\gradlew.bat bootRun`만 실행해도 같은 환경으로 시작한다. 위 명령은 현재 프로필을 명확히 보이게 하는 권장 형식이다.

정상 실행 로그에는 `The following 1 profile is active: "local"`과 `Tomcat started on port 8080`이 표시된다. 실행 후 `http://localhost:8080/`에서 고객 화면을 확인한다.

전체 화면 경로는 `http://localhost:8080/screens`에서 확인한다. `local` 프로필에서는 공개 고객 화면을 로그인 없이 열 수 있다. 모든 관리자 화면(`/admin/**`)은 관리자 로그인이 필요하며, 로컬에서는 베이스라인 마이그레이션이 만든 `admin@cakeshop.local / Admin1234!` 계정으로 로그인해 확인한다. `rds` 프로필에서는 고객 목업 공개 조회도 비활성화된다.

### 공용 RDS로 실행

1. `.env_sample`을 `.env`로 복사한다.
2. `.env`의 `RDS_ENDPOINT`, `RDS_PORT`, `RDS_DATABASE`, `RDS_USERNAME`, `RDS_PASSWORD`를 실제 접속 정보로 변경한다.
3. 다음 명령을 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=rds"
```

정상 실행 로그에는 `The following 1 profile is active: "rds"`가 표시된다. `.env`는 Git에 커밋하지 않으며 저장소에는 실제 값이 없는 `.env_sample`만 유지한다.

RDS 프로필은 `require_secure_transport=ON` 환경에 맞춰 MariaDB Connector/J의 `sslMode=trust`로 TLS 연결을 사용한다. 이 설정은 통신을 암호화하지만 서버 인증서와 호스트명은 검증하지 않으므로 팀 공용 개발 RDS 용도에만 사용한다. 운영 환경에서는 AWS RDS CA 인증서를 등록하고 `sslMode=verify-full`을 사용해야 한다.

TLS 설정이 빠진 JDBC URL을 사용하면 다음 오류가 발생한다.

```text
Connections using insecure transport are prohibited while --require_secure_transport=ON
```

### PowerShell 환경변수로 전환한 경우

다음처럼 환경변수를 사용하면 해당 PowerShell 창에서 이후 실행에도 같은 프로필이 유지된다.

```powershell
$env:SPRING_PROFILES_ACTIVE="rds"
```

다시 `local` 프로필로 돌아가려면 실행 중인 서버를 종료한 후 환경변수를 제거하고 프로필을 명시해 실행한다.

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

이미 실행된 Java 프로세스의 프로필은 환경변수를 제거해도 바뀌지 않는다. 반드시 기존 서버를 먼저 종료하고 다시 실행한다.

## 프로필 요약

- `local` (기본): 각 PC에 직접 설치한 MariaDB와 `.env`의 `LOCAL_DB_*` 설정 사용
- `rds`: `.env`의 `RDS_ENDPOINT`, `RDS_PORT`, `RDS_DATABASE`, `RDS_USERNAME`, `RDS_PASSWORD` 사용

## DB 스키마 관리

Flyway로 관리한다. 앱이 부팅할 때 미적용 마이그레이션을 순서대로 적용하고 `flyway_schema_history`에 기록하므로, 각자 손으로 SQL을 돌릴 일이 없다.

| 위치 | 내용 | 적용 대상 |
|---|---|---|
| `src/main/resources/db/migration/V1__baseline_schema.sql` | 전환 시점의 전체 스키마(35개 테이블) + 필수 시드(카테고리, 공통 샘플 계정 `admin@cakeshop.local`·`user@cakeshop.local`, 대표 매장 1행 + 7개 요일 영업시간) | 모든 환경 |
| `src/main/resources/db/migration/V2__…` 이후 | 이후의 모든 스키마 변경 | 모든 환경 |
| `src/main/resources/db/seed/R__dev_seed.sql` | 개발용 샘플 상품 | **`local` 전용** |
| `docs/sql/legacy/` | 전환 이전 V0~V19 이력 | ❌ 보관용 |

- **커밋된 마이그레이션 파일은 수정하지 않는다.** 변경은 새 `V<다음번호>__<설명>.sql`(언더바 2개)로 추가한다. 고치면 체크섬 검증에 걸려 이미 적용한 팀원의 부팅이 실패한다.
- **공용 RDS는 자동 실행이 꺼져 있다**(`rds` 프로필 `spring.flyway.enabled: false`). 아무나의 `bootRun`이 공용 스키마를 바꾸지 못하게 하기 위해서다. 반영은 스키마 담당자가 의도적으로 한 번만 켜서 수행한다.

  ```powershell
  .\gradlew.bat bootRun --args="--spring.profiles.active=rds --spring.flyway.enabled=true"
  ```

### 적용 방법

| 상황 | 적용 순서 |
|---|---|
| **신규 DB 구축**(새 팀원, RDS 재구축) | `V0_ERD.sql` 하나면 스키마가 완성된다(소급 반영되어 있음). 커뮤니티 개발용 시드가 필요하면 `V2`도 적용한다. |
| **기존 DB 갱신** | 아직 적용하지 않은 V2 이후 파일만 번호 순서대로 적용한다. |

증분 파일은 재적용해도 안전하도록 `IF EXISTS` / `IF NOT EXISTS` 가드를 둔다. V0가 소급 반영되어 있어 가드가 없으면 신규 DB에서 증분을 재적용할 때 `Duplicate column` / `Can't DROP COLUMN`으로 실패한다.

> 검증됨: 개발 로컬 DB에 `V7`(장바구니)·`V8`(일반 주문/모의 결제)·`V9`(1:1 채팅)·`V10`(알림 타입 제약·인덱스)·`V11`(상품 샘플 시드)·`V12`(알림 전달 이력)까지 적용했으며 머지 후 구조 검증·전체 테스트·화면 스모크를 통과했다. 기존 DB는 `V2 → V3 → V4 → V5 → V6 → V7 → V8 → V9 → V10 → V11 → V12` 순서로 적용한다.
>
> `V11_product_seed.sql`은 스키마가 아니라 개발용 상품 샘플이다(재실행해도 중복되지 않는다). 이 시드가 없으면 products가 비어 있어 로컬에서 장바구니 → 주문 → 결제 흐름을 확인할 수 없다.

### 시드 구분 (로컬 vs 공용 RDS)

시드는 두 종류뿐이고 기준은 **"없으면 앱이 동작하지 않는가"** 하나다. 이제 이 구분은 문서가 아니라
**파일 위치로 강제된다** — `db/seed`는 `local` 프로필에서만 `flyway.locations`에 들어간다.

| 구분 | 기준 | 위치 | 대상 | 공용 RDS |
|---|---|---|---|---|
| **필수 시드** | 없으면 화면·기능이 깨지는 마스터 데이터 | `db/migration` | `categories` 4종·대표 매장 1행 + 7개 요일 영업시간(V1), `post_categories`(V2) | **적용된다** |
| **데모 시드** | 로컬에서 흐름을 눈으로 확인하기 위한 샘플 | `db/seed` | 샘플 계정 2개, 상품·주문제작 샘플 | **적용되지 않는다** |

- 대표 매장(`id = 1`)은 `StoreService.DEFAULT_STORE_ID`가 고정 참조하고 `getStoreView`가 7개 요일 행을 필수로 요구하므로 필수 시드다. 값은 RDS에서 관리자 화면으로 실제 매장 정보로 덮어쓴다.
- 데모 시드는 반복 실행되는 repeatable 마이그레이션(`R__`)이라 전부 멱등하게 작성한다. 존재를 확인하고 넣는 형태이므로 재실행해도 중복되지 않는다.
- 공용 RDS의 관리자 계정은 샘플 계정(`admin@cakeshop.local / Admin1234!`)을 쓰지 않는다. **해시가 저장소에 공개돼 있어서** 이 계정을 아예 `db/seed`로 내렸다. RDS에는 별도 계정을 직접 만든다.
- 상품·주문제작 옵션·쿠폰은 운영에서 관리자 화면으로 등록하는 데이터다. RDS가 비어 있는 것이 정상이며 시드로 채우지 않는다.

`docs/sql/legacy`의 옛 파일을 예외적으로 돌려야 할 때는 다음 도우미를 쓴다(일상적인 스키마 반영에는 쓰지 않는다).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\legacy\V2_community_status_and_seed.sql
```

`scripts\verify-merge.ps1`는 머지 후 `db/migration`의 V2 이후 마이그레이션 산출물이 실제 로컬 DB 구조와 일치하는지 대조한다. Flyway 이력이 "적용했다"고 말하는 것과 DB가 실제로 그런지는 다른 문제라서, 이 대조는 전환 후에도 남겨 뒀다.

상태값(`status`) 컬럼은 도메인마다 흩어지지 않도록 `docs/conventions.md`의 상태값 공통 규칙(영문 enum 이름 저장·한글 라벨 미저장·전이는 service)을 따른다.

## 구조

- `global`: 공통 기반(config·security·error·web·paging·infra)
- `domain/{13개}`: controller·service·mapper·dto(form/view)·entity·error

## Store 수직 슬라이스 구현 예시

새 설정형 도메인은 `domain/store`의 흐름을 기준으로 구현한다. 단, store는 단일 매장 설정이므로 목록·페이징을 포함한 전체 CRUD는 product 실구현이 현재 브랜치에 병합된 뒤 그 코드를 참고한다.

### 적용 순서

1. 앱을 실행해 Flyway가 테이블과 필수 시드(공통 샘플 계정 `admin@cakeshop.local`·`user@cakeshop.local`, 대표 매장 1행 + 7개 요일 영업시간)를 생성하게 한다.
2. `admin@cakeshop.local / Admin1234!`로 로그인한다.
3. `GET /admin/store`에서 매장 정보를 조회한다.
4. 폼 저장은 `StoreUpdateForm` 검증 → `StoreService` 트랜잭션 → `StoreMapper.xml`의 `#{}` 바인딩 순서로 처리된다.
5. 검증 실패는 같은 화면을 재렌더하고, 성공은 `/admin/store`로 redirect한 뒤 공통 FlashMessage를 표시한다.
6. 저장 결과는 `HomeService`가 `StorePublicView`로 받아 고객 메인과 공통 Footer에 전달한다.
   `HomeService`는 매장 외에 product·coupon·review·community의 공개 View도 같은 방식으로 조합한다(전용 Mapper 없음).

`V1_first_MVC_table.sql`의 샘플 계정은 화면 확인용이므로 공용 RDS에는 그대로 적용하지 않는다. Spring Security는 이메일로 회원을 조회하고 DB의 `ADMIN` 역할(권한 문자열 `ROLE_ADMIN`)을 확인한 뒤, 로그인 전에 요청했던 `/admin/store`로 돌려보낸다.

### 역할 분리 기준

- `entity`: DB 조회 결과와 영속 상태 (MyBatis POJO — JPA `@Entity`가 아니며 더티체킹·지연로딩 없음, 저장은 mapper 호출로만)
- `dto/form`: 관리자 입력 및 Jakarta Validation 규칙
- `dto/view`: 관리자·고객 화면에 필요한 읽기 데이터
- `service`: 여러 테이블 변경의 트랜잭션과 `BusinessException + StoreErrorCode`
- `mapper/XML`: SQL과 `#{}` 바인딩, camelCase 매핑
- `controller`: Model+View, BindingResult 재렌더, RedirectAttributes FlashMessage

구현 과정과 선택 이유는 각 계층의 핵심 지점에 주석으로 남겨 두었다. 새 도메인은 자명한 문법 주석까지 복사하지 말고, 트랜잭션 경계·검증 실패 처리·도메인 조합처럼 구조상 중요한 주석만 유지한다.

## 관리자 화면 경로

로그인 성공 시 저장된 요청이 없으면 `/admin`으로 이동한다. **관리자 화면은 전부 실구현으로 전환됐다** — 아래 표를 정본으로 삼는다.

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 대시보드 | `/admin` | 실제 오늘 주문·순매출·대기 건수·최근 주문·오늘 픽업 일정·재고 부족·처리할 작업 |
| 매장 | `/admin/store` | 실제 조회·수정·휴무일 관리 |
| 상품 | `/admin/products`, `/admin/products/new`, `/admin/products/{id}/edit` | 실제 CRUD·검색·페이징·대표 이미지·판매 중지/재개 |
| 주문 | `/admin/orders`, `/admin/orders/{id}` | 실제 조회·검색·상태/주문일/픽업일 필터·페이징 |
| 제작·픽업 | `/admin/fulfillment` | 실제 픽업일 조회·`PAID → READY_FOR_PICKUP → PICKED_UP` 처리 |
| 결제·환불 | `/admin/payments` | 실제 결제·전액 취소/환불 내역 조회 및 관리자 취소(제공자 취소 API 연동) |
| 쿠폰 | `/admin/coupons`, `/admin/coupons/new`, `/admin/coupons/{id}/edit`, `/admin/coupons/{id}/issue` | 실제 CRUD·페이징·발급 중지/재개/종료·회원 지정 발급 |
| 회원 | `/admin/members`, `/admin/members/{id}` | 실제 목록·검색(이름·이메일·상태)·페이징·상세(활동 요약)·이용 제한/해제 |
| 후기 | `/admin/reviews` | 실제 목록·검색(상품명·노출·평점)·숨김/복구·답글 등록/수정 |
| 커뮤니티 | `/admin/community`, `/admin/community/{id}` | 실제 목록·검색·제재 처리 |
| 채팅 | `/admin/chat` | 실제 고객별 1:1 상담·검색/필터·읽음·종료·STOMP 실시간 이벤트 |
| 알림 | `/admin/notifications` | 실제 내 알림·읽음 처리 + 전체 발송 내역(유형·읽음 필터·페이징) |
| 통계 | `/admin/statistics` | 실제 기간·집계 단위(일/주/월) 조회·요약 지표·추이(인라인 SVG)·상품별/인기 상품·시간대별 픽업·기타 지표 |

각 화면은 해당 `domain/*/controller/*AdminController`가 소유한다. 목업이 남아 있지 않으므로 목업 스모크 테스트(`AdminPageControllerTests`)와 `fragments/admin/mock-notice`는 삭제했고, 화면마다 전용 테스트(`StoreAdminControllerTests` 패턴)가 담당한다.

## 고객 화면 선이관

프론트 원본 18개 화면 중 메인과 로그인은 각각 매장 조회와 Spring Security 연동을 유지한다. 나머지 화면은 아래 import 스크립트로 도메인별 Thymeleaf 템플릿과 GET 경로에 먼저 연결했다. 커뮤니티 3개와 채팅 1개 화면은 별도로 추가했으며, 이로써 `/screens` 기준 고객 화면은 총 22개다. 고객 화면은 전부 실제 DB 연동으로 전환됐고, 목업 스크립트(`customer-mockup.js`)에 의존하는 화면은 하나도 남아 있지 않다(`CustomerPageControllerTests`가 고정한다).

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 전체 화면 목록 | `/screens` | 고객 22개·관리자 15개, 총 37개 경로 안내 |
| 메인 | `/` | 실제 구현 (store·product·coupon·review·community의 공개 View 조합, 히어로 배너만 정적) |
| 회원가입 | `/signup` | 실제 가입 (검증·중복 확인) |
| 상품 목록·상세 | `/products`, `/products/{id}` | 실제 구현 (필터·정렬·검색·페이징, 일반 상품 DB 장바구니 담기) |
| 장바구니 | `/cart` | 실제 DB 구현 (조회·합산·수량 변경·단건/선택/전체 삭제·선택 주문 인계·최신 판매 정보 검증) |
| 픽업 설정 | `/orders/pickup` | 실제 준비일·영업일·휴무일·운영시간 기반 슬롯 선택 |
| 주문 제작 | `/orders/custom/options`, `/orders/custom/{id}`, `/orders/custom/pay/{token}` | 실제 구현 (요청서 제출·견적 확인·수락·일회성 링크 결제 — 일반 결제와 같은 2단계) |
| 주문서·완료·상세 | `/orders/checkout`, `/orders/complete`, `/orders/{id}` | 실제 세션 초안·소유권 검증·전액 취소 |
| 결제 | `/orders/payment`, `/orders/payment/success`, `/orders/payment/fail` | 실제 2단계 결제 (`READY` 선삽입 → 승인 → `DONE`, 승인 성공 시 `PAID` 주문 생성). 제공자는 `cakeshop.payment.provider`로 고른다 — `mock`(기본, 외부 호출 없음) / `toss`(결제창 + 실승인) |
| 마이페이지·프로필 | `/mypage`, `/mypage/profile` | 실제 조회·수정·비밀번호 변경·탈퇴, 진행 중·최근 주문 블록 |
| 1:1 채팅 | `/chat` | 실제 텍스트·이미지·읽음·상담 자동 재개·`/주문제작` 카드·STOMP 실시간 이벤트 |
| 쿠폰함·쿠폰 받기 | `/mypage/coupons`, `/coupons` | 실제 구현 (사용 가능/사용 완료/기간 만료 분류, 정원·1인 1장 다운로드, 결제 적용·취소 복구) |
| 알림 | `/notifications` | 실제 구현 (목록·키셋 더보기·개별/전체 읽음·헤더 미읽음 뱃지·STOMP 실시간 수신) |
| 후기 | `/reviews`, `/reviews/new`, `/reviews/{id}/edit` | 실제 구현 (픽업 완료 주문 항목당 1개, 작성·수정·삭제, 이미지 3장, 상품 평점 집계 반영) |
| 커뮤니티 목록·상세·글쓰기 | `/community`, `/community/{id}`, `/community/new` | 실제 구현 (페이징·무한스크롤·댓글·좋아요) |

고객 화면은 후기를 마지막으로 **전부 실구현으로 전환**됐다. 따라서 `$screenMap`은 비어 있고, 아래 명령은 이제 전용 CSS·JavaScript만 다시 가져온다. 새 목업 화면을 이관할 때만 `$screenMap`에 항목을 추가하고, 실구현 전환 시 다시 제외한다.

### 목업 JS 번들 취급

`customer-mockup.js`·`admin-mockup.js`는 **파일로 남기되 어떤 화면도 로드하지 않는다**. 새 목업을 들여올 때 다시 쓰는 자산이라 지우지 않지만, 실구현 화면이 끌어다 쓰면 안 된다. 두 번들 모두 `[data-confirm]` 확인창 핸들러를 갖고 있어 `app.js`와 함께 로드되면 **확인창이 두 번 뜨기 때문이다.** `CustomerPageControllerTests`가 참조 0을 고정한다.

`app.js`는 화면마다 붙이지 않고 공통 프래그먼트에서만 로드한다 — 고객은 `fragments/common/head`, 관리자는 `fragments/admin/header`다. 같은 스크립트를 두 번 붙이면 클릭 핸들러가 두 번 등록돼 같은 증상이 난다(테스트가 함께 고정한다).

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import-customer-mockups.ps1
```
