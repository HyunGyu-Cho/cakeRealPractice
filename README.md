# cakeshop

Spring Boot 4.0.2 · Java 21 · Gradle · Thymeleaf · MyBatis · MariaDB

## 개발 환경

각 개발자가 PC에 MariaDB를 직접 설치하고 Spring Boot를 실행한다. 기본적으로 각자의 로컬 MariaDB를 사용하고, 필요할 때만 `rds` 프로필로 공용 AWS RDS에 접속한다. Docker는 사용하지 않는다.

## 로컬 DB 준비

1. 각 PC에 MariaDB 11.4를 설치하고 실행한다.
2. `cakeshop` 데이터베이스와 접속 계정을 생성한다.
3. `.env_sample`을 `.env`로 복사하고 `LOCAL_DB_*` 값을 자신의 MariaDB에 맞춘다.
4. `docs/sql` 디렉터리의 DDL을 로컬 DB에 순서대로 적용한다.

## 실행 프로필 선택

애플리케이션을 시작할 때 `local`과 `rds` 중 하나를 선택한다. 기본 프로필은 `local`이며, 공용 AWS RDS는 `rds`를 명시한 경우에만 접속한다. 실행 중에는 프로필을 바꿀 수 없으므로 전환하기 전에 실행 중인 서버를 `Ctrl+C`로 종료해야 한다. 종료하지 않고 다시 실행하면 `Port 8080 was already in use` 오류가 발생한다.

### 로컬 DB로 실행

개인 개발과 화면 확인에는 `local` 프로필을 사용한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

`local`이 기본 프로필이므로 `.\gradlew.bat bootRun`만 실행해도 같은 환경으로 시작한다. 위 명령은 현재 프로필을 명확히 보이게 하는 권장 형식이다.

정상 실행 로그에는 `The following 1 profile is active: "local"`과 `Tomcat started on port 8080`이 표시된다. 실행 후 `http://localhost:8080/`에서 고객 화면을 확인한다.

전체 화면 경로는 `http://localhost:8080/screens`에서 확인한다. `local` 프로필에서는 화면 선이관 기간 동안 고객 목업 흐름만 로그인 없이 열 수 있다. 모든 관리자 화면(`/admin/**`)은 관리자 로그인이 필요하며, 로컬에서는 `docs/sql/V1_first_MVC_table.sql`로 만든 `admin@cakeshop.local / Admin1234!` 계정으로 로그인해 확인한다. `rds` 프로필에서는 고객 목업 공개 조회도 비활성화된다.

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

Flyway를 사용하지 않는다. `docs/sql`의 DDL을 RDS와 각 개발자의 로컬 DB에 수동으로 동일하게 적용한다.

- `V0_ERD.sql`: 전체 스키마 정본(35개 테이블). **보관용 정본이라 확정 변경을 소급 반영한다.**
- `V1_first_MVC_table.sql`: 1차 병렬 착수용 테이블 18개. 마찬가지로 보관용 정본이며, 로그인 가능한 공통 샘플 계정과 매장 필수 시드(대표 매장 1행 + 7개 요일 요일 영업시간)를 포함한다.
- `V2` 이후: 이미 만들어진 DB에 적용하는 **증분 마이그레이션**. 적용된 파일은 수정하지 않고 새 번호를 추가한다.

### 적용 방법

| 상황 | 적용 순서 |
|---|---|
| **신규 DB 구축**(새 팀원, RDS 재구축) | `V0_ERD.sql` 하나면 스키마가 완성된다(소급 반영되어 있음). 커뮤니티 개발용 시드가 필요하면 `V2`도 적용한다. |
| **기존 DB 갱신** | 아직 적용하지 않은 V2 이후 파일만 번호 순서대로 적용한다. |

증분 파일은 재적용해도 안전하도록 `IF EXISTS` / `IF NOT EXISTS` 가드를 둔다. V0가 소급 반영되어 있어 가드가 없으면 신규 DB에서 증분을 재적용할 때 `Duplicate column` / `Can't DROP COLUMN`으로 실패한다.

> 검증됨: 개발 로컬 DB에 `V7`(장바구니)·`V8`(일반 주문/모의 결제)·`V9`(1:1 채팅)·`V10`(알림 타입 제약·인덱스)·`V11`(상품 샘플 시드)·`V12`(알림 전달 이력)까지 적용했으며 머지 후 구조 검증·전체 테스트·화면 스모크를 통과했다. 기존 DB는 `V2 → V3 → V4 → V5 → V6 → V7 → V8 → V9 → V10 → V11 → V12` 순서로 적용한다.
>
> `V11_product_seed.sql`은 스키마가 아니라 개발용 상품 샘플이다(재실행해도 중복되지 않는다). 이 시드가 없으면 products가 비어 있어 로컬에서 장바구니 → 주문 → 결제 흐름을 확인할 수 없다.

기존 DB에 증분 파일을 적용할 때는 다음 도우미를 사용할 수 있다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\V2_community_status_and_seed.sql
```

적용 후 `scripts\verify-merge.ps1`가 증분 SQL의 최종 구조와 로컬 DB를 대조한다.

상태값(`status`) 컬럼은 도메인마다 흩어지지 않도록 `docs/conventions.md`의 상태값 공통 규칙(영문 enum 이름 저장·한글 라벨 미저장·전이는 service)을 따른다.

## 구조

- `global`: 공통 기반(config·security·error·web·paging·infra)
- `domain/{13개}`: controller·service·mapper·dto(form/view)·entity·error

## Store 수직 슬라이스 구현 예시

새 설정형 도메인은 `domain/store`의 흐름을 기준으로 구현한다. 단, store는 단일 매장 설정이므로 목록·페이징을 포함한 전체 CRUD는 product 실구현이 현재 브랜치에 병합된 뒤 그 코드를 참고한다.

### 적용 순서

1. `docs/sql/V1_first_MVC_table.sql`을 적용해 테이블과 필수 시드(공통 샘플 계정 `admin@cakeshop.local`·`user@cakeshop.local`, 대표 매장 1행 + 7개 요일 영업시간)를 생성한다.
2. `admin@cakeshop.local / Admin1234!`로 로그인한다.
3. `GET /admin/store`에서 매장 정보를 조회한다.
4. 폼 저장은 `StoreUpdateForm` 검증 → `StoreService` 트랜잭션 → `StoreMapper.xml`의 `#{}` 바인딩 순서로 처리된다.
5. 검증 실패는 같은 화면을 재렌더하고, 성공은 `/admin/store`로 redirect한 뒤 공통 FlashMessage를 표시한다.
6. 저장 결과는 `HomeService`가 `StorePublicView`로 받아 고객 메인과 공통 Footer에 전달한다.

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

로그인 성공 시 저장된 요청이 없으면 `/admin`으로 이동한다. 실제 구현으로 전환된 화면과 아직 브라우저 목업인 화면은 아래 표를 정본으로 삼는다. 목업 화면의 변경 버튼은 백엔드가 연결될 때까지 비활성화한다.

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 대시보드 | `/admin` | 목업 |
| 매장 | `/admin/store` | 실제 조회·수정·휴무일 관리 |
| 상품 | `/admin/products`, `/admin/products/new`, `/admin/products/{id}/edit` | 실제 CRUD·검색·페이징·대표 이미지·판매 중지/재개 |
| 주문 | `/admin/orders`, `/admin/orders/{id}` | 실제 조회·검색·상태/주문일/픽업일 필터·페이징 |
| 제작·픽업 | `/admin/fulfillment` | 실제 픽업일 조회·`PAID → READY_FOR_PICKUP → PICKED_UP` 처리 |
| 결제·환불 | `/admin/payments` | 실제 모의 결제·전액 취소/환불 내역 조회 및 관리자 취소 |
| 쿠폰 | `/admin/coupons`, `/admin/coupons/new`, `/admin/coupons/{id}/edit`, `/admin/coupons/{id}/issue` | 실제 CRUD·페이징·발급 중지/재개/종료·회원 지정 발급 |
| 회원 | `/admin/members` | 목업 |
| 후기 | `/admin/reviews` | 목업 |
| 커뮤니티 | `/admin/community`, `/admin/community/{id}` | 실제 목록·검색·제재 처리 |
| 채팅 | `/admin/chat` | 실제 고객별 1:1 상담·검색/필터·읽음·종료·STOMP 실시간 이벤트 |
| 알림 | `/admin/notifications` | 실제 내 알림·읽음 처리 + 전체 발송 내역(유형·읽음 필터·페이징) |
| 통계 | `/admin/statistics` | 목업 |

각 목업 화면은 해당 `domain/*/controller/*AdminController`가 소유한다. 백엔드를 구현할 때 URL과 템플릿은 유지하고 Controller의 Model 데이터와 비활성화된 명령 버튼만 실제 기능으로 교체한다.

## 고객 화면 선이관

프론트 원본 18개 화면 중 메인과 로그인은 각각 매장 조회와 Spring Security 연동을 유지한다. 나머지 화면은 아래 import 스크립트로 도메인별 Thymeleaf 템플릿과 GET 경로에 먼저 연결했다. 커뮤니티 3개와 채팅 1개 화면은 별도로 추가했으며, 이로써 `/screens` 기준 고객 화면은 총 22개다. 커뮤니티·회원·상품·장바구니·일반 주문/모의 결제·채팅·알림은 실제 DB 연동으로 전환됐고, 나머지 목업 화면의 폼은 브라우저 안에서만 실행되며 DB를 변경하지 않는다.

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 전체 화면 목록 | `/screens` | 고객 22개·관리자 15개, 총 37개 경로 안내 |
| 회원가입 | `/signup` | 실제 가입 (검증·중복 확인) |
| 상품 목록·상세 | `/products`, `/products/{id}` | 실제 구현 (필터·정렬·검색·페이징, 일반 상품 DB 장바구니 담기) |
| 장바구니 | `/cart` | 실제 DB 구현 (조회·합산·수량 변경·단건/선택/전체 삭제·선택 주문 인계·최신 판매 정보 검증) |
| 픽업 설정 | `/orders/pickup` | 실제 준비일·영업일·휴무일·운영시간 기반 슬롯 선택 |
| 주문 제작 | `/orders/custom/options`, `/orders/custom/{id}`, `/orders/custom/pay/{token}` | 실제 구현 (요청서 제출·견적 확인·수락·일회성 링크 모의 결제) |
| 주문서·완료·상세 | `/orders/checkout`, `/orders/complete`, `/orders/{id}` | 실제 세션 초안·소유권 검증·전액 취소 |
| 결제 | `/orders/payment` | 실제 UUID 멱등 모의 결제 (`DONE`, 결제 성공 시 `PAID` 주문 생성) |
| 마이페이지·프로필 | `/mypage`, `/mypage/profile` | 실제 조회·수정·비밀번호 변경·탈퇴 (주문 블록은 예시 데이터) |
| 1:1 채팅 | `/chat` | 실제 텍스트·이미지·읽음·상담 자동 재개·`/주문제작` 카드·STOMP 실시간 이벤트 |
| 쿠폰함·쿠폰 받기 | `/mypage/coupons`, `/coupons` | 실제 구현 (사용 가능/사용 완료/기간 만료 분류, 정원·1인 1장 다운로드, 결제 적용·취소 복구) |
| 알림 | `/notifications` | 실제 구현 (목록·키셋 더보기·개별/전체 읽음·헤더 미읽음 뱃지·STOMP 실시간 수신) |
| 후기 | `/reviews/new` | 목업 |
| 커뮤니티 목록·상세·글쓰기 | `/community`, `/community/{id}`, `/community/new` | 실제 구현 (페이징·무한스크롤·댓글·좋아요) |

프론트 저장소가 갱신되면 다음 명령으로 `$screenMap`에 남은 목업 1개(후기)와 전용 CSS·JavaScript를 다시 가져온다. 메인·로그인, 별도로 추가한 커뮤니티·채팅, 실구현으로 전환된 회원·상품·장바구니·일반 주문/결제·알림·주문제작·쿠폰 화면은 이 명령이 덮어쓰지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import-customer-mockups.ps1
```
