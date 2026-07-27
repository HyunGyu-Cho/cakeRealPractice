# Flyway 도입 제안 (초안 — 팀 합의 전)

> **상태: 제안.** 이 문서는 논의용이며 아직 코드에 반영되지 않았다.
> 현재 `CLAUDE.md`는 "Docker와 Flyway는 사용하지 않는다"를 명시하고 있으므로,
> 도입하려면 그 규칙과 절대규칙 2개 항목을 **팀 합의(PR)로 함께 바꿔야 한다.**

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

## 8. 도입하지 않는 경우의 대안

Flyway 없이 지금 방식의 약점만 줄이려면 — 스키마 버전을 기록하는 테이블 하나
(`schema_version(version, applied_at)`)를 만들고 각 V파일 끝에 INSERT를 넣는 방법이 있다.
"적용 여부를 모른다" 문제만 해결되고, 이중 관리·체크섬 검증은 그대로 남는다.
직접 만드느니 Flyway를 쓰는 편이 낫다는 것이 이 문서의 입장이다.
