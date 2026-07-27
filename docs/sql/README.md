# docs/sql — 전환 이전 SQL 보관소

> **이 디렉터리의 SQL은 더 이상 적용하지 않는다.**
> DB 스키마는 Flyway가 `src/main/resources/db/migration`에서 관리한다.

## 지금 스키마를 바꾸려면

`src/main/resources/db/migration/`에 **새 V번호 파일**을 추가하는 것이 전부다.

```
src/main/resources/db/migration/V<다음번호>__<설명>.sql   ← 언더바 2개
```

`bootRun` 하면 Flyway가 미적용분을 순서대로 적용하고 `flyway_schema_history`에 기록한다.
손으로 적용할 것도, 정본에 소급 반영할 것도 없다.

**이미 커밋된 마이그레이션 파일은 절대 수정하지 않는다**(절대규칙). Flyway가 체크섬을
검증하므로, 고치면 이미 적용한 사람의 DB에서 부팅이 실패한다.

## 파일 배치

| 위치 | 내용 | 적용 대상 |
|---|---|---|
| `src/main/resources/db/migration/V1__baseline_schema.sql` | Flyway 전환 시점의 전체 스키마 + 필수 시드(카테고리·매장). 샘플 계정은 여기 없다 — `db/seed` 참조 | 모든 환경 |
| `src/main/resources/db/migration/V2__post_categories_seed.sql` | 게시판 카테고리 코드값 | 모든 환경 |
| `src/main/resources/db/migration/V3__…` | 앞으로의 모든 변경 | 모든 환경 |
| `src/main/resources/db/seed/R__dev_seed.sql` | 개발용 샘플 상품 | **`local` 프로필만** |
| `docs/sql/legacy/` | 전환 이전 V0~V19 | ❌ 보관용 |

## "스키마는 있는데 이력이 없다"로 부팅이 막힐 때

Flyway 전환(2026-07-27) 이전부터 쓰던 DB이거나, 손으로 세운 DB다. Flyway 입장에서는
**마이그레이션을 하나도 적용하지 않았는데 테이블이 이미 있는 DB**라 무엇을 적용해야 할지 알 수 없다.

전환 기간에는 `baseline-on-migrate: true`로 이런 DB를 자동 흡수했지만, 흡수가 끝나 다시 껐다.
켜 둔 채로 두면 **마이그레이션을 건너뛴 DB가 정상인 척 굴러가기** 때문이다. 꺼 두면 부팅이 막혀
사람이 알아차린다.

해당하는 DB가 있으면 한 번만 켜서 흡수한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.flyway.baseline-on-migrate=true"
```

베이스라인(V1)까지 적용된 것으로 기록되고 그 이후 마이그레이션만 적용된다. 한 번 흡수되면
다음부터는 그냥 실행하면 된다. **스키마가 실제로 베이스라인과 같을 때만 옳은 조치**이므로,
확신이 없으면 빈 DB를 새로 만드는 편이 낫다.

## `legacy/`는 왜 남겨 뒀나

전환 이전의 스키마 변경 이력이다. "이 컬럼이 언제, 왜 생겼나"를 추적할 때 읽는다.
`V1__baseline_schema.sql`은 이 이력을 전부 반영한 **결과**이므로, legacy를 다시 적용할 일은 없다.

애초에 다시 적용할 수도 없다 — 구 `V9_chat.sql`은 이미 있는 FK를 중복 생성하고,
구 `V14_product_type_general.sql`은 이미 바뀐 카테고리 코드를 또 바꾼다. 이 재생 불가능성이
Flyway로 전환한 이유 중 하나다. 배경은 `docs/flyway-migration-proposal.md`.

> 구 번호와 새 번호는 겹친다(예: 구 `V2_community_status_and_seed.sql` ↔ 새 `V2__post_categories_seed.sql`).
> 경로가 완전히 다르고 Flyway는 `db/migration`만 보므로 충돌하지 않는다. 문서에서 언급할 때만
> "구 V2" / "V2"로 구분한다.

## 예외: legacy 파일을 굳이 돌려야 할 때

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\legacy\V<번호>_<이름>.sql
```

새 마이그레이션에는 쓰지 않는다. 이 경로로 적용한 변경은 Flyway 이력에 남지 않아
"DB에는 있는데 Flyway는 모르는" 상태를 만든다.
