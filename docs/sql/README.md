# docs/sql 사용법

이 디렉터리의 V파일은 **두 종류**다. 성격이 다르므로 섞어서 적용하면 안 된다.

- **정본** (`V0`, `V1`) — 현재 상태를 통째로 담은 파일. 확정 변경이 소급 반영된다.
- **증분** (`V2` 이상) — "무엇을 왜 바꿨는지" 남긴 이력. 각자 **자기 앞 번호까지 적용된 DB**를 전제로 한다.

## DB 세팅

### 새로 만드는 경우

```powershell
# 순서대로 두 개만 적용한다.
docs/sql/V0_ERD.sql        # 스키마 전체
docs/sql/V18_dev_seed.sql  # 개발용 시드
```

`V0_ERD.sql` 하나에 현재 스키마가 전부 들어 있다. **증분(V2~V17)을 그 위에 다시 적용하지 않는다.**

### 이미 쓰던 DB를 최신으로 맞추는 경우

아직 적용하지 않은 증분만 번호 순으로 적용한 뒤, 마지막에 `V18_dev_seed.sql`을 얹는다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\V<번호>_<이름>.sql
```

## 각 파일의 성격

| 파일 | 성격 | 새 DB에 적용? |
|---|---|---|
| `V0_ERD.sql` | **전체 스펙 정본.** 확정 변경이 소급 반영되어 현재 스키마 전부를 담는다. | ✅ 이것만 |
| `V1_first_MVC_table.sql` | 1차 MVP 보관용. 도메인별 병렬 착수를 위해 최소 15개 테이블만 담았다. V0의 부분집합이라 `comments` 등이 빠져 있다. | ❌ |
| `V2` ~ `V17` | 이력. 상태값 확정(V2~V5)과 도메인 착수(V6~V17). | ❌ |
| `V18_dev_seed.sql` | 현행 스키마 기준 개발용 시드. **`V11`을 대체한다.** | ✅ |

## 증분을 정본 위에 재적용하면 안 되는 이유

V0는 이미 모든 확정 변경이 반영된 **최종 상태**다. 그 위에 증분을 얹는 것은 "이미 끝난 변경을 또 하라"는 뜻이라 실패하는 것이 정상이다. 실제로 다음에서 깨진다.

- **`V9_chat.sql`** — `notifications`에 `fk_notifications_chat_message` FK를 추가하는데, V0에 이미 있어 중복 생성으로 실패한다(errno 121).
- **`V14_product_type_general.sql`** — 카테고리 코드를 `NORMAL` → `GENERAL`로 바꾸는데, V0에는 이미 `GENERAL`이라 UNIQUE 충돌한다.

두 파일 모두 **잘못되지 않았다.** V8·V13 상태의 DB에서는 정상 동작하며, 실제로 그렇게 적용됐다.

## `V11_product_seed.sql`이 더 이상 쓰이지 않는 이유

`V11`은 `categories.code = 'NORMAL'`을 찾아 상품을 넣는다. 그런데 `V14`가 그 코드를 `GENERAL`로 바꿨으므로, **V14를 지난 DB에는 영영 적용할 수 없다**(`category_id cannot be null`). 이력으로 남겨두되 사용하지 않으며, 같은 역할은 `V18_dev_seed.sql`이 현행 스키마 기준으로 수행한다.

## 새 변경을 추가할 때

절대규칙(`CLAUDE.md`)에 따라 **두 곳을 함께 고친다.**

1. 새 V번호 파일을 만든다 (기존 증분은 절대 수정하지 않는다).
2. 같은 변경을 `V0_ERD.sql`에 소급 반영한다. 1차 MVP 범위에 해당하면 `V1_first_MVC_table.sql`도 함께 고친다.

2번을 빠뜨리면 정본이 현행과 어긋나고, 새 DB를 세운 사람만 다른 스키마를 갖게 된다. CI가 매 PR마다 `V0` + `V18`로 빈 DB를 세우고 전체 테스트를 돌리므로 이 누락은 빨간불로 드러난다(`.github/workflows/ci.yml`).
