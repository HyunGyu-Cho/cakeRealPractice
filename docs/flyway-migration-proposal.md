# Flyway 도입 제안 (반영 완료 — 배경 기록)

> **상태: 반영됨(2026-07-27).** 이 문서는 "왜 이렇게 바꿨나"를 남기는 배경 기록이다.
> 실제 사용법은 [`docs/sql/README.md`](sql/README.md)와 [README](../README.md)를 본다.
>
> 아래 3~5장은 **제안 당시의 내용**을 그대로 둔다. 실제 구현에서 달라진 점은 8장에 정리했다.

## 1. 왜 바꾸려 하는가

현재 방식은 "`docs/sql`의 V파일을 각자 손으로, 번호 순서대로 적용"이다. 여기서 나오는 문제:

| 문제 | 지금 상황 |
|---|---|
| **적용 여부를 아무도 모른다** | 어떤 PC가 V16까지 적용했는지 DB에 기록이 없다. 물어보는 수밖에 없다. |
| **이중 관리** | 절대규칙상 변경 하나에 "새 V파일 + `V0_ERD.sql` 소급 반영"을 **두 곳** 고쳐야 한다. 빠뜨리면 새로 세운 사람만 다른 스키마를 갖는다. |
| **적용 순서 사고** | 증분을 정본 위에 얹으면 깨진다(V9 FK 중복, V14 카테고리 코드). README에 "정상이다"라고 적어 둬야 할 만큼 직관에 반한다. |
| **부분 적용 복구 불가** | SQL 중간에서 실패하면 절반만 적용된 상태로 남는다. |
| **드리프트 감지 없음** | 누가 로컬 DB를 손으로 고쳐도 알 방법이 없다. |

Flyway는 `flyway_schema_history` 테이블에 **적용 기록 + 파일 체크섬**을 남긴다. 앱 부팅 시
미적용분만 자동 적용되고, 이미 적용된 파일이 수정되면 체크섬 불일치로 부팅을 막는다.
위 5개 문제가 한 번에 사라진다.

## 2. 현 구조를 그대로 얹을 수 없는 이유

| 항목 | 충돌 내용 |
|---|---|
| 파일명 | Flyway 규칙은 `V19__설명.sql`(언더바 **2개**). 현재는 전부 1개라 인식되지 않는다. |
| `V0_ERD.sql` | "확정 변경이 소급 반영되는 정본"이다. Flyway 마이그레이션은 적용 후 **불변**이어야 한다(체크섬). 개념이 정면 충돌. |
| `V1_first_MVC_table.sql` | V0의 부분집합. V0 → V1 순서로 실행하면 CREATE TABLE 중복. |
| `V9`, `V14` | V0 위에 재적용 불가(`docs/sql/README.md` 41~43줄). 즉 **V0~V19 전체를 빈 DB에서 순서대로 재생하는 경로가 애초에 없다.** |
| `V11_product_seed.sql` | V14 이후 DB에는 영영 적용 불가. |
| 기존 DB | 팀원 각 PC의 로컬 MariaDB와 공용 RDS에 이미 스키마가 있다. 빈 DB 전제로 돌리면 전부 실패. |

**결론: "이력 전체를 Flyway 형식으로 리네임해서 재생"은 불가능하다.** 가능하게 하려면 증분
V파일들을 수정해야 하는데, 그건 절대규칙("적용된 증분은 수정 금지") 위반이고 작업량도 크다.

## 3. 제안: 베이스라인 리셋

현재 스키마를 **출발점(baseline)으로 동결**하고, 그 이후 변경부터 Flyway가 관리한다.
과거 이력은 지우지 않고 읽기 전용 보관으로 옮긴다.

### 3.1 파일 재배치

```
src/main/resources/db/migration/
  V1__baseline_schema.sql     ← 현 docs/sql/V0_ERD.sql 을 그대로 복사 (이후 불변)
  V2__dev_seed.sql            ← 현 docs/sql/V18_dev_seed.sql 을 그대로 복사 (이후 불변)
  V3__...                     ← 앞으로의 모든 변경 (현 V19 다음 작업부터)

docs/sql/legacy/              ← 기존 V0~V19 전부 이동. 읽기 전용 이력 보관.
docs/sql/README.md            ← "이제 여기 파일은 적용하지 않는다"로 개정
```

시드를 마이그레이션에 넣을지는 선택지가 있다 → 5장 참조.

### 3.2 의존성 (`build.gradle`)

```gradle
implementation 'org.flywaydb:flyway-core'
// MariaDB/MySQL 계열은 별도 모듈이 있어야 인식된다. 없으면 "Unsupported Database" 로 뜬다.
implementation 'org.flywaydb:flyway-mysql'
```

버전은 Spring Boot 4.0.2의 dependency management가 관리하므로 명시하지 않는다.

### 3.3 설정 (`application.yml`)

공통 블록에 추가:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    # 이미 스키마가 있는 기존 DB(각 PC 로컬 + 공용 RDS)를 V1이 적용된 것으로 표시하고 넘어간다.
    # 이 옵션이 없으면 "빈 DB가 아니다"라며 부팅이 막힌다.
    baseline-on-migrate: true
    baseline-version: 1
    # 팀원이 이미 적용된 파일을 실수로 고치면 부팅을 막는다. 이게 이 도입의 핵심 효과다.
    validate-on-migrate: true
```

**주의**: `baseline-on-migrate`는 전원이 한 번씩 부팅해 baseline 기록을 남긴 뒤에는
`false`로 되돌리는 게 안전하다(빈 DB를 실수로 baseline 처리하는 사고 방지).
전환 완료 시점에 별도 PR로 끄는 것을 제안한다.

### 3.4 CI (`.github/workflows/ci.yml`)

"스키마·시드 적용" 스텝(59~69줄)을 **삭제**한다. 빈 DB에 앱이 뜨면서 Flyway가
`V1__baseline_schema.sql` + `V2__dev_seed.sql`을 스스로 적용한다.
CI가 매 PR마다 "빈 DB에서 전체 마이그레이션 재생 + 전체 테스트"를 검증하게 되므로,
지금 V0 소급 반영 누락을 잡아 주던 안전망이 그대로 유지된다(오히려 강해진다).

### 3.5 스크립트

`scripts/apply-local-migration.ps1`은 역할이 사라진다. 마이그레이션은 `bootRun`이 수행한다.
삭제하지 말고 상단에 "레거시 — `docs/sql/legacy` 전용" 주석만 남기는 것을 제안한다.

## 4. 함께 바꿔야 하는 규칙 (`CLAUDE.md`)

| 위치 | 현재 | 제안 |
|---|---|---|
| 문서 상단 | "Docker와 Flyway는 사용하지 않는다" | "Docker는 사용하지 않는다. DB 스키마는 Flyway로 관리한다" |
| 절대규칙 | "DB에 이미 적용된 증분 SQL(V2 이후)은 수정하지 않고 새 V번호 파일을 추가한다. 단 `V0_ERD.sql`·`V1_first_MVC_table.sql`은 …소급 반영한다 — 확정 변경 시 두 곳을 함께 고친다." | "`src/main/resources/db/migration`의 마이그레이션 파일은 **한 번 커밋되면 수정하지 않는다.** 변경은 항상 새 `V<다음번호>__<설명>.sql`을 추가한다. 소급 반영 대상 정본은 더 이상 없다." |
| 절대규칙 | "증분 V파일은 자기 앞 번호까지 적용된 DB를 전제로 한다… 새 DB는 V0 + 최신 시드로 세운다" | "새 DB는 빈 스키마만 만들면 되고, 나머지는 앱 부팅 시 Flyway가 적용한다." |
| 3장 빌드/테스트 | "Flyway 미사용. docs/sql의 V파일을 로컬 DB와 RDS에 수동으로 번호 순서대로 적용한다" | "빈 DB(`CREATE DATABASE cakeshop`)만 만들고 `bootRun`하면 스키마가 자동 구성된다." |

**이 규칙 변경은 `global/*`·`store` 공통 코드 변경과 같은 급의 팀 합의 사항이다.**

## 5. 열린 결정 사항

| 쟁점 | 선택지 | 의견 |
|---|---|---|
| **시드 데이터 위치** | (a) `V2__dev_seed.sql`로 마이그레이션에 포함 / (b) `db/seed/`에 두고 `local` 프로필에서만 별도 적용 | 관리자 계정 등 운영에도 필요한 최소 데이터와 개발용 더미가 섞여 있다. RDS에 더미 상품이 들어가도 되는지 확인 필요. 문제 되면 (b)로 분리. |
| **RDS 자동 마이그레이션** | 공용 RDS에 붙은 누군가의 `bootRun`이 스키마를 바꾸게 둘 것인가 | 위험. `rds` 프로필은 `spring.flyway.enabled: false`로 두고, 스키마 반영은 별도 명령(Flyway Gradle 플러그인 `flywayMigrate`)으로 담당자가 수행하는 안을 제안. |
| **전환 시점** | 도메인 작업이 몰리는 시점에 하면 충돌 | 진행 중 feature 브랜치가 적은 시점에 dev로 한 번에 넣는 게 좋다. 전환 PR 이후 각자 `git pull` 후 첫 `bootRun` 1회 필요. |
| **번호 재시작** | 새 체계의 `V1`이 옛 `V1_first_MVC_table.sql`과 헷갈림 | 옛 파일은 `docs/sql/legacy/`로 옮기므로 경로가 달라 실제 충돌은 없다. 걱정되면 새 체계를 `V100__`부터 시작하는 방법도 있다. |

## 6. 팀원이 전환 후에 해야 하는 일

기존 DB를 쓰는 경우 — **아무것도 안 해도 된다.** 최신 dev를 받아 `bootRun` 한 번 하면
Flyway가 `flyway_schema_history` 테이블을 만들고 "V1까지 적용됨"으로 기록한 뒤 넘어간다.

DB를 새로 세우는 경우:

```powershell
# 빈 스키마만 만든다. V파일 수동 적용은 이제 하지 않는다.
mariadb -u root -p -e "CREATE DATABASE cakeshop DEFAULT CHARACTER SET utf8mb4;"
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

## 7. 되돌리는 방법

전환 PR을 revert하고, 각 DB에서 `DROP TABLE flyway_schema_history;`를 실행하면 완전히
원상 복구된다. 스키마 자체는 건드리지 않으므로 데이터 손실이 없다.

## 8. 실제 구현에서 달라진 점

제안(3~5장)과 다르게 간 부분과, 작업 중 드러난 사실을 남긴다.

### 8.1 시드를 세 갈래로 나눴다

제안은 `V2__dev_seed.sql` 한 파일이었으나, 5장에서 열어 둔 쟁점대로 성격별로 갈랐다.

| 내용 | 위치 | 이유 |
|---|---|---|
| 카테고리·대표 매장·영업시간 | `V1__baseline_schema.sql` | 없으면 어느 환경에서든 화면이 깨진다 |
| `post_categories` 코드값 | `V2__post_categories_seed.sql` | 위와 같음(커뮤니티 글쓰기의 전제) |
| **샘플 계정 2개**, 샘플 상품 | `db/seed/R__dev_seed.sql` | `local` 전용 |

**샘플 계정을 `db/seed`로 내린 것이 제안에 없던 판단이다.** BCrypt 해시가 저장소에 공개돼 있어
공용 RDS에 들어가면 안 되는데, 베이스라인에 두면 RDS 마이그레이션 때 같이 들어간다.
전에는 "RDS에는 그 절만 빼고 적용한다"는 사람의 주의력에 기대고 있었고, 이제 파일 위치가 강제한다.

개발용 시드는 versioned가 아니라 **repeatable(`R__`)** 로 뒀다. 샘플을 고칠 때마다 새 V번호를
붙이지 않아도 되고, 전부 멱등하게 작성했으므로 재실행이 안전하다.

### 8.2 `spring-boot-flyway` 의존성이 추가로 필요했다

Boot 4는 자동설정을 기술별 모듈로 쪼갰다. `flyway-core`와 `flyway-mysql`만 넣으면
**앱은 정상적으로 뜨는데 마이그레이션이 조용히 실행되지 않는다.** 빈 DB로 부팅해 보기 전까지
드러나지 않는 종류의 실패라, 전환 검증에서 빈 DB 실행을 반드시 해야 했다.

### 8.3 베이스라인을 만드는 과정에서 기존 스키마 문제 2건이 드러났다

이중 관리를 없앤 효과가 바로 나온 부분이다.

- **`chk_products_type` 누락** — 구 `V14`가 추가한 제약이 구 `V0_ERD.sql`에 소급 반영되지 않았다.
  즉 V0로 새로 세운 DB에는 이 제약이 없었다. 기존 테스트는 이 제약만 V14에서 확인하고 있어
  못 잡았다. 베이스라인에 되살렸다.
- **`cart_item_images`·`cart_item_options` 잔재** — 장바구니 구현(커밋 `1e18e01`) 때 설계에서
  빠졌지만 이미 만들어진 DB에는 DROP이 나가지 않아 빈 채로 남아 있었다.
  참조하는 코드가 없고 행도 0건이라 `V3__drop_unused_cart_item_tables.sql`로 정리했다.

### 8.4 `*SqlSyncTests` 5개를 정리했다

enum ↔ `CHECK` 대조 테스트는 "증분 V파일 + V0 정본" 두 곳을 각각 확인하고 있었다.
정본이 사라져 확인할 곳이 한 줄기뿐이므로, 공통 로직을 `support/MigrationSql`로 모으고
각 테스트를 단일 단언으로 줄였다. 제약을 **마지막으로 정의한 마이그레이션**을 보는 규칙은 유지했다.

### 8.5 `baseline-on-migrate`는 흡수 후 껐다

전환 PR에서는 기존 DB를 흡수하려고 켜 뒀고, 흡수가 끝난 뒤 별도 PR로 `false`로 되돌렸다
(3.3의 "주의"에 적어 둔 대로다). 켜 둔 채로 두면 이력 없는 DB를 묻지도 않고 베이스라인 처리해서,
**마이그레이션을 건너뛴 DB가 정상인 척 굴러간다.** 빈 DB는 이 설정과 무관하게 전부 적용되므로
새 DB 세팅에는 영향이 없다. 뒤늦게 흡수가 필요하면 그때만 한 번 켠다(`docs/sql/README.md`).

### 8.6 검증한 것

- 기존 DB(전환 이전 V파일로 세운 로컬): baseline 기록 후 V2·V3·`R__` 적용 → 전체 테스트 통과
- 빈 DB: 마이그레이션 전체 재생 → 전체 테스트 통과 (CI가 매 PR에서 밟는 경로)
- 두 경로의 결과 스키마가 **테이블 38개로 일치**함을 `information_schema` 대조로 확인

## 9. 도입하지 않는 경우의 대안

Flyway 없이 지금 방식의 약점만 줄이려면 — 스키마 버전을 기록하는 테이블 하나
(`schema_version(version, applied_at)`)를 만들고 각 V파일 끝에 INSERT를 넣는 방법이 있다.
"적용 여부를 모른다" 문제만 해결되고, 이중 관리·체크섬 검증은 그대로 남는다.
직접 만드느니 Flyway를 쓰는 편이 낫다는 것이 이 문서의 입장이다.
