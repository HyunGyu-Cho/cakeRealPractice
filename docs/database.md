# 데이터베이스

이 프로젝트의 DB에 관한 모든 것을 담는다. **1~6장은 처음 보는 사람용**이고,
7장부터는 규칙과 배경 설명이다.

---

# 1부. 처음이라면

## 1. 우리 프로젝트에서 DB가 하는 일

회원가입한 사람, 등록된 케이크, 들어온 주문 — 이런 것들은 **앱을 껐다 켜도 남아 있어야 한다.**
그래서 프로그램 메모리가 아니라 **데이터베이스(DB)** 라는 별도 프로그램에 저장한다.
우리가 쓰는 DB 프로그램은 **MariaDB**다.

DB 안은 엑셀 시트 여러 장이라고 생각하면 얼추 맞다. 시트 한 장을 **테이블(table)** 이라고 부른다.

| 테이블 이름 | 담는 것 |
|---|---|
| `members` | 회원 |
| `products` | 케이크 상품 |
| `orders` | 주문 |
| `reviews` | 후기 |

이런 테이블이 지금 **38개** 있다.

## 2. 단어 3개만 알면 된다

### 스키마 (schema)

**테이블의 뼈대.** 엑셀로 치면 "이 시트에는 이름·전화번호·가입일 칸이 있다"는 정보다.
데이터(내용)가 아니라 칸의 구조를 말한다.

### 마이그레이션 (migration)

**뼈대를 바꾸는 작업 지시서 한 장.** 예를 들어 "`members` 시트에 생일 칸을 추가해라" 같은
내용이 SQL이라는 언어로 적힌 파일이다. 한 번 만들면 번호가 붙고, 그 번호 순서대로 실행된다.

### Flyway

**지시서를 순서대로 대신 실행해 주는 도구.** 두 가지를 해 준다.

1. 아직 실행 안 한 지시서만 골라서 순서대로 실행
2. **어디까지 실행했는지 DB에 적어 둠** (`flyway_schema_history`라는 테이블에 기록된다)

우리 프로젝트에서는 **앱을 실행하면 Flyway가 자동으로 돌아간다.** 따로 할 일이 없다.

## 3. 처음 세팅하기

### 3-1. MariaDB 설치

각자 PC에 **MariaDB 11.4**를 설치한다. 설치 중 물어보는 root 비밀번호는 적어 둔다.

### 3-2. 빈 데이터베이스 만들기

테이블은 만들 필요 없다. **껍데기만** 만들면 나머지는 앱이 알아서 채운다.

```powershell
mariadb -u root -p -e "CREATE DATABASE cakeshop DEFAULT CHARACTER SET utf8mb4;"
```

### 3-3. 접속 정보 적기

프로젝트 맨 위 폴더의 `.env_sample` 파일을 복사해 이름을 `.env`로 바꾸고, 자기 값으로 고친다.

```
LOCAL_DB_HOST=localhost
LOCAL_DB_PORT=3307        ← MariaDB 설치할 때 정한 포트 (보통 3306)
LOCAL_DB_DATABASE=cakeshop
LOCAL_DB_USERNAME=root
LOCAL_DB_PASSWORD=여기에_자기_비밀번호
```

> `.env`는 비밀번호가 들어 있어서 **깃허브에 올리면 안 된다.** 자동으로 제외되게 설정돼 있다.

### 3-4. 앱 실행

```powershell
.\gradlew.bat bootRun
```

이 한 줄이 테이블 38개를 만들고, 기본 데이터와 샘플 데이터까지 다 넣는다.

### 3-5. 잘 됐는지 확인

실행 중 뜨는 글자 중에 이런 줄들이 보이면 성공이다.

```
Migrating schema `cakeshop` to version "1 - baseline schema"
Migrating schema `cakeshop` to version "2 - post categories seed"
Migrating schema `cakeshop` to version "3 - drop unused cart item tables"
Migrating schema `cakeshop` with repeatable migration "dev seed"
Successfully applied 4 migrations to schema `cakeshop`, now at version v3
Tomcat started on port 8080 (http)
Started CakeshopApplication in 12.128 seconds
```

브라우저에서 **http://localhost:8080** 을 열어 화면이 나오면 끝이다.
전체 화면 목록은 http://localhost:8080/screens 에서 볼 수 있다.

관리자 화면(`/admin`으로 시작하는 주소)은 로그인이 필요하다.

- 아이디: `admin@cakeshop.local`
- 비밀번호: `Admin1234!`

**두 번째 실행부터는** 이미 다 만들어져 있으므로 이 줄이 뜬다. 정상이다.

```
Schema `cakeshop` is up to date. No migration necessary.
```

앱을 끌 때는 실행 중인 창에서 **`Ctrl + C`** 를 누른다. 안 끄고 또 실행하면 8080번을
이미 쓰고 있다며 에러가 난다(6장 참고).

## 4. 스키마를 바꾸고 싶을 때

예를 들어 회원에게 "생일" 칸을 추가한다고 하자.

**1단계 — 다음 번호 확인.** `src/main/resources/db/migration/` 폴더에서 가장 큰 번호를 찾는다.
지금은 `V3`까지 있으니 다음은 **`V4`** 다.

**2단계 — 파일 만들기.** `src/main/resources/db/migration/V4__add_member_birthday.sql`

```sql
ALTER TABLE `members` ADD COLUMN `birthday` DATE NULL;
```

**3단계 — 앱 재시작.** 로그에 이렇게 뜨면 반영된 것이다.

```
Migrating schema `cakeshop` to version "4 - add member birthday"
```

**끝이다.** 다른 파일을 고칠 필요도, DB에 직접 접속할 필요도 없다.
팀원은 이 파일을 받아서 앱을 켜기만 하면 똑같이 반영된다.

## 5. 절대 하면 안 되는 것 2가지

### ① 이미 올린 마이그레이션 파일 고치기

`V1`, `V2`, `V3`처럼 **이미 깃허브에 올라간 파일은 절대 수정하지 않는다.**
고칠 게 있으면 새 번호 파일을 만들어 거기서 바꾼다.

왜냐면 Flyway가 파일마다 지문 같은 값(체크섬)을 저장해 두는데, 파일을 고치면 지문이
달라진다. 그러면 **이미 그 파일을 실행한 팀원의 앱이 안 켜진다.**

### ② DB에 직접 접속해서 테이블 고치기

손으로 고치면 Flyway는 그 사실을 모른다. 내 DB만 남들과 다른 상태가 되고,
나중에 원인 찾기가 아주 어려워진다. 변경은 **항상 마이그레이션 파일로** 한다.

## 6. 에러가 났을 때

### `Found non-empty schema(s) ... but no schema history table`

**뜻**: "테이블은 있는데 언제 만들었는지 기록이 없다. 뭘 해야 할지 모르겠다"

Flyway를 도입하기 전(2026-07-27 이전)에 만든 DB이거나, 손으로 만든 DB다.
아래처럼 한 번만 실행하면 "지금 상태를 시작점으로 인정"하고 넘어간다.

```powershell
.\gradlew.bat bootRun --args="--spring.flyway.baseline-on-migrate=true"
```

**스키마가 실제로 베이스라인과 같을 때만 옳은 조치**다.
확신이 없으면 빈 DB를 새로 만드는 편이 안전하다(3장).

### `Migration checksum mismatch`

**뜻**: "이미 실행한 파일의 내용이 바뀌었다"

5장의 ①을 어긴 것이다. 그 파일을 원래대로 되돌리고, 바꾸고 싶은 내용은 새 번호 파일로 옮긴다.

### `Port 8080 was already in use`

**뜻**: "8080번 자리를 이미 다른 앱이 쓰고 있다"

앱이 이미 켜져 있는 것이다. 실행 중인 창에서 `Ctrl + C`로 끄고 다시 실행한다.

---

# 2부. 규칙과 설정

## 7. 파일 배치

```
src/main/resources/db/
├── migration/      ← 모든 환경에 적용
│   ├── V1__baseline_schema.sql              Flyway 전환 시점의 전체 스키마 + 필수 시드
│   ├── V2__post_categories_seed.sql         게시판 종류(후기·질문·레시피·자유)
│   └── V3__drop_unused_cart_item_tables.sql 안 쓰는 테이블 정리
└── seed/           ← local 프로필에서만 적용
    └── R__dev_seed.sql                      샘플 계정, 샘플 케이크 상품

docs/sql/legacy/    ← Flyway 전환 이전 V0~V19. 읽기 전용 보관, 실행 금지
```

### 파일 이름 규칙

- `V<번호>__<설명>.sql` — 밑줄이 **2개**다. 하나면 Flyway가 인식하지 못한다
- `R__<설명>.sql` — repeatable. 버전 마이그레이션이 모두 끝난 뒤 실행되고, **내용이 바뀌면 다시 실행**된다

## 8. 시드 구분 (로컬 vs 공용 RDS)

시드는 두 종류뿐이고 기준은 **"없으면 앱이 동작하지 않는가"** 하나다. 이 구분은 문서가 아니라
**파일 위치로 강제된다** — `db/seed`는 `local` 프로필에서만 `flyway.locations`에 들어간다.

| 구분 | 기준 | 위치 | 대상 | 공용 RDS |
|---|---|---|---|---|
| **필수 시드** | 없으면 화면·기능이 깨지는 마스터 데이터 | `db/migration` | `categories` 4종·대표 매장 1행 + 7개 요일 영업시간(V1), `post_categories`(V2) | **적용된다** |
| **데모 시드** | 로컬에서 흐름을 눈으로 확인하기 위한 샘플 | `db/seed` | 샘플 계정 2개, 상품·주문제작 샘플 | **적용되지 않는다** |

- 대표 매장(`id = 1`)은 `StoreService.DEFAULT_STORE_ID`가 고정 참조하고 `getStoreView`가 7개 요일 행을 필수로 요구하므로 필수 시드다. 값은 RDS에서 관리자 화면으로 실제 매장 정보로 덮어쓴다.
- 데모 시드는 반복 실행되는 repeatable 마이그레이션이라 전부 멱등하게 작성한다.
- **공용 RDS의 관리자 계정은 샘플 계정(`admin@cakeshop.local / Admin1234!`)을 쓰지 않는다.** 해시가 저장소에 공개돼 있어서 이 계정을 아예 `db/seed`로 내렸다. RDS에는 별도 계정을 직접 만든다.
- 상품·주문제작 옵션·쿠폰은 운영에서 관리자 화면으로 등록하는 데이터다. RDS가 비어 있는 것이 정상이며 시드로 채우지 않는다.

## 9. Flyway 설정

### 의존성 (`build.gradle`)

```gradle
implementation 'org.springframework.boot:spring-boot-flyway'  // 자동설정
implementation 'org.flywaydb:flyway-core'                     // 엔진
implementation 'org.flywaydb:flyway-mysql'                    // MariaDB 지원
```

셋 다 필요하다. Boot 4가 자동설정을 기술별 모듈로 쪼개서, **`spring-boot-flyway`가 없으면
앱은 정상적으로 뜨는데 마이그레이션이 조용히 실행되지 않는다.** `flyway-mysql`이 빠지면
부팅 시 "Unsupported Database"로 죽는다. 버전은 Boot BOM이 관리한다.

### 설정 (`application.yml`)

| 위치 | 설정 | 의미 |
|---|---|---|
| 공통 | `locations: classpath:db/migration` | 모든 환경이 적용하는 마이그레이션 |
| 공통 | `validate-on-migrate: true` | 커밋된 파일을 고치면 부팅 차단(체크섬) |
| 공통 | `baseline-on-migrate: false` | 이력 없는 DB는 부팅 차단. 6장 참고 |
| `local` | `locations: ...,classpath:db/seed` | 개발용 샘플 데이터 추가 |
| `rds` | `enabled: false` | 공용 서버 자동 반영 차단 |

### 프로필별 실제 동작

| 환경 | 동작 |
|---|---|
| **local**(기본) | `db/migration` + `db/seed` 적용 |
| **CI** | 빈 컨테이너 DB에 `local`로 테스트 → 매 PR이 **전체 마이그레이션 재생**을 검증 |
| **rds** | 아무것도 안 함. 담당자가 명시적으로 켤 때만 적용 |

## 10. 공용 RDS

공용 서버는 **자동 반영이 꺼져 있다.** 아무나 앱을 켰다가 공용 DB 구조가 바뀌면 위험해서다.
반영이 필요하면 담당자가 아래처럼 한 번만 켠다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=rds --spring.flyway.enabled=true"
```

### 확정된 운영 규칙

- DB 구조의 정본은 `src/main/resources/db/migration`이다.
- 커밋된 마이그레이션 파일은 수정하지 않고 새 파일을 추가한다(체크섬 검증).
- SQL은 로컬 DB에서 검증하고 PR 검토를 거친 후 RDS에 반영한다.
- 대량 테스트 데이터는 공용 RDS에 넣지 않는다.
- 적용 이력은 RDS의 `flyway_schema_history`에 자동 기록된다(적용 시각·체크섬·성공 여부).
- **롤백**: Flyway Community는 자동 undo가 없다. 되돌리는 변경을 새 V파일로 추가하는 것이 기본이고, 파괴적 변경은 적용 전 RDS 스냅샷을 뜬다.
- 테이블·컬럼·제약조건 네이밍은 [`conventions.md`](conventions.md)의 데이터베이스 규약을 따른다.

## 11. 검증 장치

Flyway 이력이 "적용했다"고 말하는 것과 DB가 실제로 그런지는 다른 문제라서, 두 겹을 더 뒀다.

- **`scripts/verify-merge.ps1`** — 머지 후 `db/migration`의 V2 이후 마이그레이션 산출물이 실제 로컬 DB 구조와 일치하는지 대조한다.
- **각 도메인의 `*SqlSyncTests`** — `support/MigrationSql`을 통해 Java enum과 SQL `CHECK` 목록이 일치하는지 검증한다. 제약을 **마지막으로 정의한 마이그레이션**을 기준으로 본다(값은 나중 파일에서 늘어날 수 있다).

---

# 3부. 배경

## 12. 왜 Flyway로 바꿨나

2026-07-27 전에는 `docs/sql`의 SQL 파일을 팀원이 **각자 손으로 순서대로 실행**했다.
다섯 가지 문제가 있었다.

| 문제 | 내용 |
|---|---|
| 적용 여부를 아무도 모른다 | 어떤 PC가 몇 번까지 적용했는지 DB에 기록이 없다 |
| 이중 관리 | 변경 하나에 "새 V파일 + `V0_ERD.sql` 소급 반영" 두 곳을 고쳐야 했다 |
| 적용 순서 사고 | 증분을 정본 위에 얹으면 깨진다(V9 FK 중복, V14 카테고리 코드) |
| 부분 적용 복구 불가 | SQL 중간에서 실패하면 절반만 적용된 상태로 남는다 |
| 드리프트 감지 없음 | 누가 로컬 DB를 손으로 고쳐도 알 방법이 없다 |

Flyway는 적용 기록과 파일 체크섬을 DB에 남긴다. 다섯 문제가 한 번에 사라진다.

## 13. 왜 이력을 그대로 옮기지 않고 베이스라인을 새로 잡았나

옛 V파일을 Flyway 형식으로 이름만 바꿔 재생하는 것은 **불가능했다.**

- `V0_ERD.sql`은 "확정 변경이 소급 반영되는 정본"이라 Flyway의 **불변 원칙과 정면 충돌**한다
- `V1_first_MVC_table.sql`은 V0의 부분집합이라 순서대로 실행하면 CREATE TABLE 중복
- 구 `V9`는 이미 있는 FK를 중복 생성하고, 구 `V14`는 이미 바뀐 카테고리 코드를 또 바꾼다

즉 **빈 DB에서 V0~V19를 순서대로 재생하는 경로가 애초에 없었다.** 가능하게 하려면 이미 적용된
증분 파일을 수정해야 하는데 그건 더 큰 규칙 위반이다. 그래서 현재 스키마를 `V1__baseline_schema.sql`로
동결하고 그 이후부터 관리하는 **베이스라인 리셋**을 택했다. 옛 파일은 `docs/sql/legacy/`에
기록으로 남겼다.

## 14. 전환하며 드러난 것

### 기존 스키마 문제 2건

이중 관리를 없앤 효과가 바로 나온 부분이다.

- **`chk_products_type` 누락** — 구 `V14`가 추가한 제약이 구 `V0_ERD.sql`에 소급 반영되지 않아, V0로 새로 세운 DB에는 이 제약이 없었다. 기존 테스트가 이 제약만 V14에서 확인하고 있어 못 잡았다. 베이스라인에 복원했다.
- **`cart_item_images`·`cart_item_options` 잔재** — 장바구니 구현(커밋 `1e18e01`) 때 설계에서 빠졌지만 이미 만들어진 DB에는 DROP이 나가지 않아 빈 채로 남아 있었다. `V3`로 정리했다.

### 검증한 것

- 기존 DB(전환 이전 V파일로 세운 로컬): baseline 기록 후 V2·V3·`R__` 적용 → 전체 테스트 통과
- 빈 DB: 마이그레이션 전체 재생 → 전체 테스트 통과 (CI가 매 PR에서 밟는 경로)
- 두 경로의 결과 스키마가 **테이블 38개로 일치**함을 `information_schema` 대조로 확인

### 되돌리는 방법

전환 커밋들을 revert하고 각 DB에서 `DROP TABLE flyway_schema_history;`를 실행하면 원상 복구된다.
스키마 자체는 건드리지 않으므로 데이터 손실이 없다.

## 15. `docs/sql/legacy/`를 남겨 둔 이유

전환 이전의 스키마 변경 이력이다. "이 컬럼이 언제, 왜 생겼나"를 추적할 때 읽는다.
`V1__baseline_schema.sql`은 이 이력을 전부 반영한 **결과**이므로 다시 적용할 일이 없고,
13장에서 설명한 이유로 적용할 수도 없다.

예외적으로 legacy 파일을 돌려봐야 하면 `scripts/apply-local-migration.ps1`을 쓴다.
새 마이그레이션에는 쓰지 않는다 — 이 경로로 적용한 변경은 Flyway 이력에 남지 않아
"DB에는 있는데 Flyway는 모르는" 상태를 만든다.

> 구 번호와 새 번호는 겹친다(예: 구 `V2_community_status_and_seed.sql` ↔ 새 `V2__post_categories_seed.sql`).
> 경로가 다르고 Flyway는 `db/migration`만 보므로 충돌하지 않는다. 문서에서 언급할 때만 "구 V2" / "V2"로 구분한다.
