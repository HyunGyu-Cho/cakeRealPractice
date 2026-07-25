---
name: merge-feature
description: 기능 브랜치 PR을 dev에 머지하고 머지된 상태를 검증하는 절차. 사용자가 "머지해줘"라고 지시했을 때, 또는 머지 검증 게이트에 막혔을 때 사용한다.
argument-hint: "[PR 번호]"
---

# 머지 + 머지 후 검증 절차

대상 PR: $ARGUMENTS (비어 있으면 `gh pr list`로 확인)

## 0단계. 전제 확인 (건너뛰지 말 것)

- **머지는 사용자가 지시했을 때만 한다.** Claude는 기능 작업을 PR 생성까지만 하고 멈춘다. 지시 없이 머지하지 않는다.
- 대상 base 브랜치가 `dev`인지 확인한다. `main` 직접 머지는 금지(절대규칙).

## 1단계. 머지 전 점검

```powershell
git status --short                     # 로컬 미커밋 변경 여부
gh pr view <번호>                      # 상태·base·충돌·리뷰 여부
gh pr checks <번호>                    # CI 상태
git log --oneline dev -1               # dev 최신 커밋
git diff dev...<브랜치> --stat          # 무엇이 들어오는지
```

특히 확인할 것:
- **작업 트리 청결** — `git checkout dev`·`git pull`을 방해하거나 다른 사람의 변경을 섞을 미커밋 파일이 없어야 한다. 있으면 먼저 사용자에게 범위와 처리 방법을 확인한다.
- **PR 상태** — base가 `dev`, PR이 열림 상태, 충돌 없음, 필수 리뷰·CI 통과인지 확인한다.
- **V파일 번호 충돌** — `docs/sql`에 dev와 브랜치가 같은 V번호를 각각 추가하지 않았는지.
- **공통 파일 동시 수정** — `global/security/**`, `domain/home/**`, `README.md`, `CLAUDE.md`, `TodoList.md`, 목업 스모크 테스트. git이 자동 병합에 성공해도 의미상 깨질 수 있는 지점이다.

## 2단계. 머지와 동기화

```powershell
gh pr merge <번호> --merge
git checkout dev
git pull
```

이 시점에 **머지 검증 게이트가 자동으로 무장**된다(PostToolUse 훅). 검증을 실행하지 않고 응답을 끝내려 하면 Stop 훅이 최대 3회 차단하며, 상한을 넘긴 미검증 종료도 로컬 로그에 `ESCALATED`로 남는다.

## 3단계. 검증 (게이트 해제 조건)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1
```

이 스크립트가 하는 일과 이유:

| 검사 | 잡아내는 것 |
|---|---|
| 병합 잔재 · V번호 중복 | 충돌 표시, 병합 안 된 경로, **두 브랜치가 같은 V번호를 붙인 경우**(파일명이 달라 git은 충돌 없이 둘 다 받는다) |
| 마이그레이션 반영 | 증분 V파일(V2+)의 테이블·컬럼 존재/삭제, 상태 CHECK 허용값, 주요 컬럼 정의·생성 열 표현식이 로컬 DB와 일치하는지 |
| `gradlew test` | 컴파일 오류, 의미상 충돌(한쪽이 지운 클래스를 다른 쪽 테스트가 참조 등) |
| 부팅 + 화면 스모크 | 테스트가 **원리상 못 잡는 것** — Thymeleaf 렌더 오류, 실제로 실행되는 MyBatis SQL, 관리자 화면(테스트 커버리지 거의 0). 상태코드뿐 아니라 본문 완결성(`</html>`)과 오류 페이지 여부까지 본다 |

통과하면 게이트가 자동 해제된다.

> SQL 문법 전체를 해석하는 마이그레이션 엔진은 아니다. 현재 공통 변경 패턴(CREATE TABLE, ADD/DROP/MODIFY COLUMN, ADD/DROP CONSTRAINT, `IN(...)` CHECK, STORED 생성 열)은 대조하지만 새 SQL 문법을 도입하면 `verify-merge.ps1`의 구조 검사도 함께 확장한다.

## 4단계. 실패했을 때

1. 출력의 실패 항목과 `.claude/state/merge-gate.log`·`verify-boot.log`로 원인을 좁힌다.
2. **미적용 마이그레이션이 원인이면** `docs/sql`의 새 V파일을 로컬 DB에 번호 순서로 적용하고 다시 검증한다.
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\V<번호>_<이름>.sql
   ```
   이 실행기는 `.env`의 `LOCAL_DB_*`를 사용하고 `docs/sql/V*.sql` 밖의 파일을 거부한다. 적용 전에 경로·설정만 확인하려면 `-ValidateOnly`를 붙인다.
3. 코드 문제면 dev에서 고쳐 커밋한다(작은 수정) — 되돌려야 할 만큼 크면 사용자에게 상황을 보고하고 판단을 받는다.
4. **검증을 통과시킬 수 없는 예외 상황에서만** 사유를 남기고 해제한다. 사유 없이 상태 파일을 지우지 않는다.
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1 -Skip "사유"
   ```

## 게이트의 한계 (알고 쓸 것)

이 장치는 **"검증을 잊고 넘어가는 것"을 막는 도구**이지 변조 방지 장치가 아니다. 아래는 설계상 받아들인 한계다.

- **GitHub 웹에서 머지하면 무장되지 않는다.** 터미널에서 실행한 `gh pr merge`/`git merge`만 감지한다. 웹에서 머지했다면 이 스킬을 직접 호출해 3단계 검증을 수행한다.
- **머지 명령을 문자열로 언급만 해도 무장될 수 있다.** 안전한 방향의 오탐이며, 검증을 돌리거나 `-Skip`으로 해제하면 된다. 반대 방향(진짜 머지를 놓치는 것)보다 낫다고 판단해 이렇게 뒀다.
- **상태 파일을 직접 지우면 우회된다.** 막을 수 없으므로 대신 `-Skip "사유"` 경로를 두어, 건너뛸 때 사유가 로그에 남게 했다.
- **Stop 훅은 최대 3회만 차단한다.** 세션을 영구히 가두지 않기 위한 안전 상한이다. 상한을 넘겨 종료하면 pending 상태는 유지되고 `ESCALATED` 로그가 남으므로, 다음 작업 전에 3단계 검증 또는 사유 있는 `-Skip`으로 정리한다.
- **팀원에게는 전파되지 않는다.** `.claude/state/`는 gitignore 대상이라 각자의 로컬 상태로만 동작한다. 남의 미완료 검증 때문에 다른 사람이 막히는 일은 없다.
- **같은 PC의 다른 Claude 세션도 막지 않는다.** pending 상태에 기록된 `sessionId`와 현재 Stop 세션이 다르면 차단하지 않는다.
- **로컬 DB가 필요하다.** 스모크가 실 DB를 타므로 DB를 못 띄우는 상황에서는 검증이 실패한다. 그때는 사유를 남기고 `-Skip`한 뒤, DB 복구 후 다시 검증한다.

## 5단계. 마무리

- 머지된 브랜치 삭제: `git push origin --delete <브랜치>` (로컬은 `git branch -d`)
- `TodoList.md` 항목 `[x]` 처리와 `README.md` 화면 현황표가 실제 상태와 맞는지 확인
- 사용자에게 보고: 무엇이 머지됐고, 검증에서 무엇을 확인했고, 남은 후속 작업(예: RDS에 적용해야 할 V파일)이 무엇인지
