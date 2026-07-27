# 도메인 구현 작업 흐름 — 처음 보는 사람을 위한 안내

> 이 문서는 **"이 저장소에서 기능 하나를 만들면 실제로 어떤 일이 벌어지는가"** 를 처음부터 끝까지
> 따라가며 설명한다. `notification` 도메인을 예시로 삼는다.
>
> 이 문서를 쓸 당시 notification은 "다음 차례"였고, 지금은 실제로 구현돼 dev에 병합됐다.
> 그래서 여기 적힌 각 단계의 결과물을 실물로 대조해볼 수 있다 —
> 스펙은 `docs/specs/notification.md`, 코드는 `domain/notification`, 과정은 PR #9다.
>

> 부품 하나하나의 상세 명세(각 훅의 입력·출력, 스크립트 옵션, 상태 파일 수명)는
> `docs/claude-code-automation.md`에 있다. 이 문서는 **순서와 이유**를, 그 문서는 **명세**를 담당한다.

---

## 1. 자동화는 세 층으로 되어 있다

| 층 | 파일 | 언제 작동하나 | 성격 |
|---|---|---|---|
| **CLAUDE.md** | 프로젝트 루트 | 세션 시작 시 **항상** 컨텍스트에 주입 | 규칙 (지식) |
| **Skill** | `.claude/skills/*/SKILL.md` | 이름이 불릴 때만 로드 | 절차서 (체크리스트) |
| **Hook** | `.claude/hooks/*.ps1` | 특정 이벤트에 **무조건 자동 실행** | 관문 (강제) |

핵심 차이는 **강제력**이다.

CLAUDE.md와 Skill은 "읽고 따르는" 것이라 사람이든 Claude든 깜빡하면 그냥 지나간다.
Hook은 Claude Code 런타임이 직접 실행하므로 작업자의 의지와 무관하게 작동한다.

그래서 이 저장소는 **가장 어기기 쉬운 규칙 두 가지**만 훅으로 막아 뒀다.

1. 스펙 없이 코드부터 쓰기
2. 머지하고 검증 생략하기

```text
CLAUDE.md  ──"스펙 먼저 써라"──▶  읽고 따름 (놓칠 수 있음)
                                        │ 놓치면
                                        ▼
Hook       ──PreToolUse 차단──▶  파일 쓰기 자체가 거부됨 (못 지나감)
```

`.claude/settings.json`에 등록된 훅은 세 개다.

```text
PreToolUse  (matcher: Write|Edit)  →  guard-new-domain.ps1   // 스펙 관문
PostToolUse (matcher: Bash)        →  merge-gate-arm.ps1     // 머지 감지
Stop        (턴 종료 시)            →  merge-gate-stop.ps1    // 검증 강제
```

> 훅은 **빠른 판정만** 한다. 빌드·부팅 같은 무거운 작업은 `scripts/verify-merge.ps1`이 맡는다.
> 훅 안에서 돌리면 느리고, 실패 원인이 사용자에게 보이지 않기 때문이다.

---

## 2. notification을 만드는 전 과정

### 0단계 — 무엇을 만들지 고른다

`CLAUDE.md` 4장이 **"개발 순서 정본은 `TodoList.md`"** 라고 지정한다. TodoList 3단계를 보면:

```text
- [x] chat
- [ ] notification      ← 다음 차례
- [ ] order(수제) + payment link
```

의존 관계 기준 순서라 임의로 건너뛰지 않는다.
notification이 chat 다음인 이유는 "새 채팅 메시지 알림"이 chat 완성을 전제하기 때문이다.

---

### 1단계 — 훅에 막히는 지점 (여기가 핵심)

절차를 무시하고 바로 코드부터 쓰려 하면 이런 일이 벌어진다.

```text
Write("src/main/java/com/cakeshop/domain/notification/entity/Notification.java")
```

이 순간 **`guard-new-domain.ps1`이 실행된다.** 스크립트가 하는 일:

1. 파일 경로를 정규식으로 검사 → `src/main/java/com/cakeshop/domain/notification/` 매칭
2. 예외 허용 목록(`home`) 확인 → notification은 해당 없음
3. `docs/specs/notification.md` 존재 확인 → **없음**
4. 차단 응답 반환

```json
{
  "permissionDecision": "deny",
  "permissionDecisionReason": "spec-driven 관문: docs/specs/notification.md 가 없습니다. ..."
}
```

**파일은 생성되지 않는다.** 대신 "`/new-domain` 스킬로 스펙부터 작성하라"는 안내가 돌아온다.
훅이 작업자를 올바른 절차로 되돌려 놓는 구조다.

> 파일이 있어도 frontmatter가 `status: approved`가 아니면 똑같이 막힌다.
> 훅은 frontmatter의 `status:` 한 줄만 파싱한다. `approved-at` 같은 다른 키는 훅 동작과 무관하다.
>
> 예외는 `home` 하나뿐이다. 다른 도메인의 공개 View를 조합하기만 하는 얇은 계층이라
> 독립적인 비즈니스 스펙의 대상이 아니기 때문이다.

---

### 2단계 — 스킬 호출

```text
/new-domain notification
```

`.claude/skills/new-domain/SKILL.md`가 컨텍스트에 로드되고 7단계 절차가 시작된다.

#### 0단계: 브랜치

`feature/notification` 생성. **기능 하나 = 브랜치 하나 = PR 하나** 원칙을 구현 시작부터 적용한다.

#### 1단계: 스펙 작성·확정 — 가장 중요한 단계

스킬은 "스펙을 쓰라"고만 하지 않고 **재료를 어디서 가져올지** 지정한다.

- 목업 템플릿 `templates/customer/notification/list.html`과 `static/js/customer-mockup.js`를 읽어
  **임시 동작을 추출한다** (목업이 이미 정의해 둔 UI 계약을 존중한다)
- `docs/team-plan.md` 8장에서 notification 관련 미결 항목을 찾아 확정안을 제안한다
- 상태값이 필요하면 `docs/conventions.md`의 상태값 공통 규칙대로 정의한다

notification에서 확정해야 할 것들:

| 항목 | 주의점 |
|---|---|
| `NotificationType` enum 값 | 새 채팅 메시지·견적 도착·결제 요청·결제 완료를 최소 포함 |
| 읽음 여부 | **BOOLEAN이다.** CLAUDE.md 4장 "함정 분류"가 못박아 둔 항목 — status 문자열로 만들면 안 된다 |
| 알림 생성 시점 | 어느 도메인 서비스가 어느 순간에 호출하는가 |

그리고 스킬이 명시한 문장:

> **작성한 스펙을 사용자에게 보여주고 명시적 확정을 받는다. 확정 전에는 구현 코드를 만들거나 수정하지 않는다.**

혼자 `approved`로 바꾸면 안 된다. 사용자 확정 후에야:

```yaml
---
domain: notification
status: approved
approved-at: 2026-07-26
---
```

**이 시점부터 훅이 열린다. 스펙 파일이 곧 열쇠다.**

---

### 3단계 — DB

스키마 변경이 필요하면 `src/main/resources/db/migration/`에 새 마이그레이션 파일 하나만 만든다.
파일명은 Flyway 규칙 `V<다음번호>__<설명>.sql` — **언더바 2개**다(예: `V20__notification_topic.sql`).

절대규칙 하나가 걸린다.

- 이미 커밋된 마이그레이션 파일은 **수정하지 않고** 새 V번호를 추가한다. Flyway가 체크섬을
  검증하므로 고치면 이미 적용한 팀원의 DB에서 부팅이 실패한다. 소급 반영할 정본은 없다.

상태 컬럼이 있다면 형식이 정해져 있다.

```sql
`status` VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
CONSTRAINT `chk_notifications_status` CHECK (`status` IN ('UNREAD', 'READ'))
```

시작 상태가 여러 개라 하나로 정할 수 없으면 `DEFAULT`를 생략하고 서비스가 세팅한다
(`OrderStatus`가 이 경우다 — 일반 주문은 `PAID`, 수제는 `UNDER_REVIEW`로 시작한다).

---

### 4단계 — 백엔드 구현

`domain/store` 수직 슬라이스 순서를 그대로 따른다.

```text
entity(순수 POJO + Lombok)
  → Mapper 인터페이스 + XML (#{} 만, 컬럼 명시, 단건은 Optional)
  → ErrorCode enum (NOTIFICATION_001 접두어)
  → Service (@Transactional, BusinessException, 상태 전이 검증)
  → dto/form (Bean Validation) · dto/view (record)
  → Controller (PRG, successMessage/errorMessage 문자열 키)
```

**여기서 도메인 경계 규칙이 걸린다.**

알림은 chat·order·payment 이벤트로 생성되는데, 절대규칙상 다른 도메인의 Mapper를 직접 호출할 수 없다.
따라서 방향이 정해져 있다.

```text
[잘못]  NotificationService → ChatMapper        (다른 도메인 Mapper 직접 호출 — 금지)
[올바름] ChatService → NotificationService       (상대가 공개한 Service 인터페이스로만 연동)
```

`NotificationService`가 공개 인터페이스를 제공하고, chat·payment 서비스가 그것을 호출한다.

---

### 5단계 — 화면 연결

URL(`/notifications`)과 템플릿 경로는 **그대로 두고** 하드코딩된 본문만
`th:each` / `th:text` + Model 데이터로 교체한다. 비활성화된 버튼을 살리는 것도 이 단계다.

그리고 잊기 쉬운 것 하나:

> **`scripts/import-customer-mockups.ps1`의 `$screenMap`에서 `notification.html` 줄을 먼저 제거해야 한다.**

이 스크립트는 `$screenMap`에 남아 있는 목업만 덮어쓴다. 빼놓지 않으면 다음에 프론트 저장소를
동기화할 때 **실구현 화면이 목업으로 덮어써진다.**

현재 `$screenMap`은 비어 있다 — 고객 목업이 전부 실구현으로 전환됐다.

---

### 6단계 — 테스트

- Service 테스트 (`StoreServiceTests` 패턴)
- Controller 테스트 (`StoreAdminControllerTests` 패턴, standaloneSetup + mock)
- 실구현으로 전환된 화면은 목업 스모크 테스트에서 **제외한다**
  → `CustomerPageControllerTests`의 목록에서 `/notifications` 줄을 뺀다

---

### 7단계 — 마무리와 PR

```text
.\gradlew.bat test 전체 통과
  → 로컬 실행(--spring.profiles.active=local) 후 화면 동작 확인
  → 스펙의 "완료 기준" 체크리스트 점검, 스펙 문서를 구현과 일치하게 최종 갱신
  → TodoList.md 항목 [x], README 화면 현황표 갱신
  → 한글 커밋 메시지
  → gh pr create --base dev
```

**그리고 멈춘다.**

> CLAUDE.md 절대규칙: 기능 작업은 **PR 생성까지만** 한다.
> 머지는 사람이 PR을 검토하고 지시했을 때만 한다.

---

## 3. 머지 지시 후 — 두 번째 훅 세트

사용자가 "머지해줘"라고 하면 `/merge-feature` 스킬이 로드되고, 나머지 훅 두 개가 작동한다.

### merge-gate-arm.ps1 (PostToolUse)

Bash로 실행하는 모든 명령의 문자열을 검사한다.

```powershell
$isMerge = ($command -match '(?i)\bgh\s+pr\s+merge(?![-\w])') -or
           ($command -match '(?i)\bgit\s+merge(?![-\w])')
```

`(?![-\w])`가 붙은 이유가 이 설계의 성격을 잘 보여준다.
이게 없으면 `git merge-base --is-ancestor` 같은 **읽기 전용 조회에도 게이트가 무장되는 오탐**이
실제로 있었다고 스크립트 주석에 남아 있다. `merge-base`·`merge-tree`·`merge-file`과
`git merge --abort`·`--quit`은 모두 제외 대상이다.

머지가 감지되면 `.claude/state/merge-gate-pending.json`에 대기 상태를 남긴다.
여기에 `sessionId`가 함께 기록되는데, 같은 PC의 다른 Claude 세션이
남의 미완료 검증 때문에 막히지 않도록 하기 위한 것이다.

### merge-gate-stop.ps1 (Stop)

턴을 끝내려 할 때마다 실행된다. pending 파일이 있으면 **종료를 거부**하고
"검증을 실행하라"고 되돌려보낸다.

- 최대 **3회**까지 차단한다
- 상한을 넘기면 통과시키되 `.claude/state/merge-gate.log`에 `ESCALATED`를 남긴다
- pending 상태는 그대로 유지되므로, 다음 작업 전에 검증이나 사유 있는 `-Skip`으로 정리해야 한다

세션이 영구히 갇히지 않게 하는 안전 상한이다.

### verify-merge.ps1 — 게이트를 여는 유일한 방법

| 단계 | 검사 | 잡아내는 것 |
|---|---|---|
| 1 | 병합 잔재 · V번호 중복 | 두 브랜치가 **같은 V번호를 각각** 붙인 경우. 파일명이 다르면 git은 충돌 없이 둘 다 받으므로 git만으로는 절대 못 잡는다 |
| 2 | 마이그레이션 반영 | V2 이상 전부의 테이블·컬럼·CHECK 허용값을 실제 로컬 DB와 대조 |
| 3 | `gradlew test` | 컴파일 오류, 의미상 충돌(한쪽이 지운 클래스를 다른 쪽 테스트가 참조 등) |
| 4 | 부팅 + 화면 스모크 16개 | 테스트가 **원리상 못 잡는 것** — Thymeleaf 렌더 오류, 실제 실행되는 MyBatis SQL, 관리자 화면 |

2단계의 설계 논리가 특히 중요하다.
"이번 머지가 들여온 V파일"만 검사하면 반드시 틀린다 — `gh pr merge`는 서버에서 머지해
로컬 HEAD가 움직이지 않고, squash 머지면 머지 커밋이 아예 없기 때문이다.
그래서 **매번 V2 이상 전부**를 확인한다. 이미 적용된 것은 그냥 통과하므로 반복 실행이 안전하다.

4단계는 상태코드 200만 보지 않는다. 렌더링 도중 예외가 나도 부분 응답이 200으로 나갈 수 있어
**본문이 `</html>`까지 완결됐는지**와 오류 페이지 여부까지 확인한다.

> **실제 사례.** 2026-07-26 문서 4개만 바꾼 머지에서도 2단계가 실패했다.
> 원인은 그 변경이 아니라 로컬 DB가 V0/V1 골격으로 되돌아가 있던 것이었다(V0_ERD.sql 재실행 흔적).
> 이 검사가 없었다면 다음 사람이 notification 작업 도중 영문 모를 SQL 오류를 만났을 것이다.

검증이 DB 미반영으로 실패하면 복구 경로는 이렇다.

파일을 만든 뒤 앱을 재시작하면 Flyway가 알아서 적용한다. 손으로 SQL을 돌리지 않는다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

기동 로그의 `Migrating schema ... to version 20 - notification topic` 줄로 적용을 확인한다.
실패하면 앱이 뜨지 않으므로, 반쯤 적용된 채로 모르고 지나가는 일은 없다.

---

## 4. 전체 흐름 한눈에

```text
TodoList.md에서 다음 도메인 선택 (notification)
        │
        ▼
[훅] 코드부터 쓰려 하면 → PreToolUse 차단
        │
        ▼
/new-domain → 스펙 작성 → 사용자 확정 → status: approved
        │
        ▼
[훅 통과] V파일 → entity → mapper → service → controller → 화면 → 테스트
        │
        ▼
PR 생성하고 정지 (머지는 사람 지시 대기)
        │
        ▼
"머지해줘" → /merge-feature → 사전 점검 → gh pr merge
        │
        ▼
[훅] PostToolUse가 머지 감지 → 검증 대기 상태 무장
        │
        ▼
[훅] Stop이 종료 차단 → verify-merge.ps1 4단계 전부 통과해야 해제
        │
        ▼
브랜치 삭제 · TodoList/README 갱신 · 사용자 보고
```

---

## 5. 알아둘 한계

`/merge-feature` 스킬이 스스로 밝혀 둔 것들이다. **이 장치는 "절차를 잊는 것"을 막는 도구이지
변조 방지 장치가 아니다.**

- **GitHub 웹에서 머지하면 게이트가 무장되지 않는다.** 터미널에서 실행한 명령만 감지한다.
  웹에서 머지했다면 `/merge-feature`를 직접 호출해 검증 단계를 수행한다.
- **머지 명령을 문자열로 언급만 해도 무장될 수 있다.** 안전한 방향의 오탐이며,
  검증을 돌리거나 `-Skip`으로 해제하면 된다. 반대 방향(진짜 머지를 놓치는 것)보다 낫다고 판단한 설계다.
- **상태 파일을 직접 지우면 우회된다.** 막을 수 없으므로 대신 `-Skip "사유"` 경로를 두어,
  건너뛸 때 사유가 로그에 남게 했다. 사유 없이 상태 파일을 지우면 검증했다는 거짓 기록이 남는다.
- **팀원에게 전파되지 않는다.** `.claude/state/`는 gitignore 대상이라 각자의 로컬 상태로만 동작한다.
- **로컬 DB가 필요하다.** 스모크가 실제 DB를 타므로 DB를 못 띄우면 검증이 실패한다.
  그때는 사유를 남기고 `-Skip`한 뒤, DB 복구 후 다시 검증한다.
- **스펙 관문은 Java 도메인 경로의 Write·Edit에 집중되어 있다.** 리소스 XML이나 다른 경로까지
  완전히 통제하지는 않는다.

---

## 6. 한 줄 요약

> **CLAUDE.md는 "무엇이 옳은가", Skill은 "어떤 순서로", Hook은 "안 지키면 못 지나간다"를 담당한다.**

자동화의 목적은 규칙을 숨기고 강제하는 것이 아니라,
**중요한 절차를 눈에 보이게 만들고 누락을 줄이는 것**이다.

---

## 관련 문서

| 문서 | 내용 |
|---|---|
| `CLAUDE.md` | 절대규칙·아키텍처·컨벤션 요약 (항상 로드되는 정본) |
| `TodoList.md` | 개발 순서 정본 — 다음 작업은 여기서 고른다 |
| `docs/claude-code-automation.md` | 훅·스킬·스크립트의 **부품별 상세 명세** |
| `docs/conventions.md` | 코딩 컨벤션 정본, 상태값 인벤토리 |
| `docs/specs/_template.md` | 스펙 템플릿 |
| `docs/store-usecase-flow.md` | 표준 수직 슬라이스(store)의 코드 흐름 해설 |
| `docs/frontend-template-format.md` | 화면 템플릿 규격 정본 |
