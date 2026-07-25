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
gh pr view <번호>                      # 상태·base·충돌 여부
git log --oneline dev -1               # dev 최신 커밋
git diff dev...<브랜치> --stat          # 무엇이 들어오는지
```

특히 확인할 것:
- **V파일 번호 충돌** — `docs/sql`에 dev와 브랜치가 같은 V번호를 각각 추가하지 않았는지.
- **공통 파일 동시 수정** — `global/security/**`, `domain/home/**`, `README.md`, `CLAUDE.md`, `TodoList.md`, 목업 스모크 테스트. git이 자동 병합에 성공해도 의미상 깨질 수 있는 지점이다.

## 2단계. 머지와 동기화

```powershell
gh pr merge <번호> --merge
git checkout dev
git pull
```

이 시점에 **머지 검증 게이트가 자동으로 무장**된다(PostToolUse 훅). 검증이 통과할 때까지 턴을 끝낼 수 없다.

## 3단계. 검증 (게이트 해제 조건)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1
```

이 스크립트가 하는 일과 이유:

| 검사 | 잡아내는 것 |
|---|---|
| 병합 잔재 · V번호 중복 | 충돌 표시, 병합 안 된 경로, **두 브랜치가 같은 V번호를 붙인 경우**(파일명이 달라 git은 충돌 없이 둘 다 받는다) |
| 마이그레이션 반영 | 증분 V파일(V2+)이 만드는 테이블·컬럼이 로컬 DB에 실제로 있는지 |
| `gradlew test` | 컴파일 오류, 의미상 충돌(한쪽이 지운 클래스를 다른 쪽 테스트가 참조 등) |
| 부팅 + 화면 스모크 | 테스트가 **원리상 못 잡는 것** — Thymeleaf 렌더 오류, 실제로 실행되는 MyBatis SQL, 관리자 화면(테스트 커버리지 거의 0). 상태코드뿐 아니라 본문 완결성(`</html>`)과 오류 페이지 여부까지 본다 |

통과하면 게이트가 자동 해제된다.

> 마이그레이션 검사는 "테이블·컬럼이 없는 것"은 잡지만 **정의 변경(CHECK 제약, 생성 열 조건 등)은 잡지 못한다.** 그런 변경은 부팅 스모크나 실제 사용에서 드러난다.

## 4단계. 실패했을 때

1. 출력의 실패 항목과 `.claude/state/merge-gate.log`·`verify-boot.log`로 원인을 좁힌다.
2. **미적용 마이그레이션이 원인이면** `docs/sql`의 새 V파일을 로컬 DB에 번호 순서로 적용하고 다시 검증한다.
   ```powershell
   & "C:\Program Files\MariaDB 12.2\bin\mariadb.exe" -h localhost -P 3307 -u root -p1234 cakeshop < docs\sql\V<번호>_*.sql
   ```
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
- **팀원에게는 전파되지 않는다.** `.claude/state/`는 gitignore 대상이라 각자의 로컬 상태로만 동작한다. 남의 미완료 검증 때문에 다른 사람이 막히는 일은 없다.
- **로컬 DB가 필요하다.** 스모크가 실 DB를 타므로 DB를 못 띄우는 상황에서는 검증이 실패한다. 그때는 사유를 남기고 `-Skip`한 뒤, DB 복구 후 다시 검증한다.

## 5단계. 마무리

- 머지된 브랜치 삭제: `git push origin --delete <브랜치>` (로컬은 `git branch -d`)
- `TodoList.md` 항목 `[x]` 처리와 `README.md` 화면 현황표가 실제 상태와 맞는지 확인
- 사용자에게 보고: 무엇이 머지됐고, 검증에서 무엇을 확인했고, 남은 후속 작업(예: RDS에 적용해야 할 V파일)이 무엇인지
