# Cakeshop Claude Code 협업 자동화 정리

> 기준일: 2026-07-26  
> 범위: `CLAUDE.md`, 프로젝트 스킬, 훅, 머지 검증 보조 스크립트  
> 용도: 팀 공유 및 Notion 문서화. 이 파일 전체를 복사해 Notion 페이지에 붙여 넣어도 된다.

## 1. 한눈에 보기

이 프로젝트의 Claude Code 자동화는 네 층으로 나뉜다.

| 층 | 역할 | 핵심 파일 |
|---|---|---|
| 정책 | 프로젝트에서 지켜야 할 규칙과 기준을 설명한다. | `CLAUDE.md` |
| 절차 | 특정 작업을 어떤 순서로 수행할지 안내한다. | `.claude/skills/*/SKILL.md` |
| 관문 | 중요한 규칙을 잊거나 건너뛰려 할 때 자동으로 차단한다. | `.claude/hooks/*.ps1` |
| 실행 | 훅에서 직접 처리하기 무거운 테스트·부팅·DB 작업을 수행한다. | `scripts/verify-merge.ps1`, `scripts/apply-local-migration.ps1` |

핵심 설계는 다음 한 문장으로 요약할 수 있다.

> `CLAUDE.md`가 원칙을 정하고, Skill이 올바른 절차를 안내하며, Hook이 중요한 누락을 막고, Script가 실제 검증을 수행한다.

현재 자동화가 보호하는 흐름은 두 가지다.

```text
[도메인 구현]
구현 요청
  → PreToolUse 훅이 스펙 승인 여부 확인
  → 미승인: 코드 변경 차단 + /new-domain 안내
  → 승인: 표준 수직 슬라이스 순서로 구현
  → 테스트
  → dev 대상 PR 생성

[기능 머지]
사용자가 머지 지시
  → /merge-feature로 사전 점검
  → gh pr merge 또는 git merge
  → PostToolUse 훅이 검증 대기 상태 생성
  → scripts/verify-merge.ps1 실행
  → 통과: 게이트 해제
  → 미검증 종료: Stop 훅이 최대 3회 차단
```

## 2. 파일 구성

```text
CLAUDE.md

.claude/
├── settings.json
├── settings.local.json
├── hooks/
│   ├── guard-new-domain.ps1
│   ├── merge-gate-arm.ps1
│   └── merge-gate-stop.ps1
├── skills/
│   ├── new-domain/
│   │   └── SKILL.md
│   └── merge-feature/
│       └── SKILL.md
└── state/                         # 로컬 실행 상태, Git 추적 제외
    ├── merge-gate-pending.json    # 필요할 때 생성
    ├── merge-gate-blocks.txt      # 필요할 때 생성
    ├── merge-gate.log
    └── verify-boot.log

docs/specs/_template.md
scripts/apply-local-migration.ps1
scripts/verify-merge.ps1
```

공유 설정인 `.claude/settings.json`은 Git으로 관리한다. 개인 권한 설정인 `.claude/settings.local.json`과 실행 중 생기는 `.claude/state/`는 `.gitignore`로 제외한다.

## 3. `CLAUDE.md`: 프로젝트 정책과 컨텍스트

### 역할

Claude가 이 저장소에서 작업할 때 항상 참고하는 최상위 프로젝트 지침이다. 단순한 개발 환경 설명이 아니라 브랜치 전략, 아키텍처 경계, DB 변경 방식, 화면 구현 방식, 테스트 기준까지 담은 작업 기준서다.

### 프로젝트 기본 정보

- Spring Boot 4.0.2
- Java 21
- Gradle
- Thymeleaf SSR
- MyBatis
- MariaDB
- Docker와 Flyway는 사용하지 않음

### 작업 방향

팀원별 최종 담당 도메인은 정해져 있지만, 현재 저장소에서는 현규가 전 도메인을 먼저 예시 품질로 구현하고 팀 표준으로 제공하는 방식을 사용한다. 따라서 담당 도메인 밖의 작업도 허용하되 `domain/store` 수직 슬라이스와 공통 코딩 컨벤션을 따른다.

### 절대 규칙 요약

- 커밋 메시지는 한글로 작성한다.
- 브랜치는 `main ← dev ← feature/*` 구조를 사용한다.
- `main`에 직접 push하지 않고 PR로 병합한다.
- `.env`, 비밀값, 실제 개인정보를 커밋하지 않는다.
- MyBatis SQL 바인딩은 `#{}`만 사용하고 `${}`는 금지한다.
- JPA를 사용하지 않는다.
- 상태값은 영문 `UPPER_SNAKE`로 저장하고 상태 전이 검증은 Service가 담당한다.
- 다른 도메인의 테이블을 직접 JOIN하거나 Mapper를 직접 호출하지 않는다.
- 이미 적용된 증분 SQL(V2 이상)은 수정하지 않고 새 V번호 파일을 추가한다.
- 확정된 DB 변경은 `증분 V파일 + V0/V1 정본`에 함께 반영한다.
- 시간 컬럼은 Java가 직접 설정하지 않고 DB 기본값과 `ON UPDATE`에 맡긴다.
- `global/*`, `store`, `home`은 팀 공통 영역으로 취급한다.
- 도메인 구현 전에 `docs/specs/<도메인>.md`를 작성하고 `status: approved`로 확정한다.
- 기능 작업은 PR 생성까지만 진행하고, 사용자의 명시적 지시가 있을 때만 머지한다.
- 머지했다면 머지된 `dev`에서 통합 검증까지 완료한다.

### 아키텍처 기준

```text
Controller → Service → Mapper
```

- 역방향 호출과 계층 건너뛰기를 금지한다.
- 각 도메인은 `controller/service/mapper/entity/dto/form/dto/view/error` 구조를 따른다.
- MyBatis SQL은 `src/main/resources/mapper/<도메인>/`의 XML에 둔다.
- `domain/store`를 표준 수직 슬라이스로 사용한다.
- 목록·페이징 CRUD는 실제 구현이 병합된 경우 `domain/product`도 참고한다.
- `home`은 다른 도메인의 공개 View를 조합하는 얇은 계층이다.

### 구현 컨벤션 요약

- Entity: 순수 POJO와 Lombok `@Getter`, `@Setter`
- Form DTO: Bean Validation을 적용하는 화면 입력 모델
- View DTO: 불변 `record`
- Mapper: 명시적 컬럼, 단건 `Optional<T>`, 생성 키 `useGeneratedKeys`
- Service: 조회는 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`
- Error: 도메인별 `ErrorCode` enum과 `BusinessException`
- Controller: PRG 패턴, 성공·실패 Flash Attribute 사용
- 인증: `@AuthenticationPrincipal MemberDetails`
- DB: `snake_case`, `BIGINT AUTO_INCREMENT`, 상태 컬럼은 `VARCHAR(20)`과 CHECK 제약

## 4. Skill 1 — `/new-domain`

### 목적

새 도메인을 구현하거나 목업 도메인을 실제 구현으로 전환할 때 사용하는 스펙 우선 작업 절차다. 승인된 스펙 없이 운영 코드를 만들려다 훅에 차단된 경우에도 이 스킬로 돌아온다.

### 호출 형태

```text
/new-domain <도메인명>
```

### 전체 절차

1. 현재 브랜치와 작업 트리를 확인한다.
2. `feature/<도메인명>` 브랜치인지 확인한다.
3. `docs/specs/<도메인>.md` 존재 여부와 frontmatter 상태를 확인한다.
4. 스펙이 없으면 `docs/specs/_template.md`로 `status: draft` 문서를 만든다.
5. 목업 화면, mockup JavaScript, `docs/team-plan.md`, 상태값 컨벤션을 대조해 스펙을 채운다.
6. 사용자에게 스펙을 보여주고 명시적으로 확정받는다.
7. 확정 후 `status: approved`, `approved-at: YYYY-MM-DD`로 변경한다.
8. DB → Entity → Mapper → ErrorCode → Service → DTO → Controller 순서로 구현한다.
9. 목업 화면의 URL과 템플릿 구조를 유지하면서 실제 데이터로 연결한다.
10. Service 테스트와 Controller 테스트를 작성한다.
11. 전체 테스트와 로컬 화면 동작을 확인한다.
12. 스펙 완료 기준, `TodoList.md`, `README.md`를 실제 구현에 맞게 갱신한다.
13. 한글 커밋 후 `dev` 대상 PR을 생성한다.

### 스펙 템플릿이 요구하는 내용

- 도메인의 목적과 주요 유스케이스
- 상태값과 상태 전이 규칙
- 사용할 테이블과 스키마 변경 여부
- 도메인 간 공개 Service 인터페이스
- 대상 화면과 URL
- 목업에서 실제 규칙으로 확정할 내용
- `team-plan.md`의 미결 비즈니스 규칙
- 완료 기준 체크리스트

### 이 스킬과 연결된 관문

`guard-new-domain.ps1`이 실제 코드 변경 직전에 스펙 상태를 검사한다. 즉 `/new-domain`은 절차를 안내하고, 훅은 승인 절차를 건너뛰지 못하게 한다.

## 5. Skill 2 — `/merge-feature`

### 목적

기능 PR을 `dev`에 머지하고, 머지된 통합 상태를 실제로 검증하는 절차다. 사용자가 명시적으로 머지를 요청했거나 머지 검증 게이트에 막힌 경우 사용한다.

### 호출 형태

```text
/merge-feature <PR 번호>
```

### 머지 전 점검

```powershell
git status --short
gh pr view <번호>
gh pr checks <번호>
git log --oneline dev -1
git diff dev...<브랜치> --stat
```

확인 항목:

- 로컬 작업 트리가 안전한 상태인지
- PR base가 `dev`인지
- PR이 열려 있고 충돌이 없는지
- 필수 리뷰와 CI가 통과했는지
- `docs/sql`의 V번호가 중복되지 않는지
- 공통 파일이 의미상 충돌하지 않는지

### 머지와 검증

```powershell
gh pr merge <번호> --merge
git checkout dev
git pull
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1
```

검증에 통과하면 머지 게이트가 자동으로 해제된다. 실패하면 원인을 수정하고 같은 스크립트를 다시 실행한다.

### 예외 해제

검증을 통과시킬 수 없는 명확한 예외 상황에서만 사유를 기록하고 해제한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1 -Skip "구체적인 사유"
```

상태 파일을 직접 삭제하는 방식은 사용하지 않는다. `-Skip`은 게이트를 해제하면서 로그에 사유를 남긴다.

## 6. Hook 연결 설정

`.claude/settings.json`에는 다음 이벤트가 등록되어 있다.

| 이벤트 | Matcher | 실행 파일 | 역할 |
|---|---|---|---|
| `PreToolUse` | `Write\|Edit` | `guard-new-domain.ps1` | 승인되지 않은 도메인 운영 코드 변경 차단 |
| `PostToolUse` | `Bash` | `merge-gate-arm.ps1` | 머지 명령 감지 후 검증 대기 상태 생성 |
| `Stop` | 전체 | `merge-gate-stop.ps1` | 미검증 머지가 있으면 응답 종료 차단 |

훅에서는 긴 테스트나 서버 부팅을 직접 실행하지 않는다. 훅은 빠르게 상태를 판정하고, 실제 검증은 별도 스크립트가 수행한다.

## 7. Hook 1 — `guard-new-domain.ps1`

### 실행 시점

Claude가 `Write` 또는 `Edit` 도구로 파일을 생성·수정하기 직전인 `PreToolUse`에서 실행된다.

### 검사 대상

```text
src/main/java/com/cakeshop/domain/<도메인>/
```

위 경로에 해당하지 않으면 바로 통과한다.

### 통과 조건

- `docs/specs/<도메인>.md`가 존재한다.
- 문서가 `---`로 시작하는 frontmatter를 가진다.
- frontmatter 안에 정확히 `status: approved`가 있다.
- 또는 대상 도메인이 예외 목록의 `home`이다.

### 차단 결과

조건을 만족하지 않으면 `permissionDecision: deny`를 반환하고 다음 내용을 안내한다.

- 어떤 도메인의 스펙이 없거나 미승인인지
- `/new-domain`을 먼저 실행해야 한다는 점
- 사용할 템플릿 경로 `docs/specs/_template.md`

### 안전 특성 및 한계

- JSON 입력을 읽지 못하거나 내부 오류가 나면 작업을 막지 않는 fail-open 방식이다.
- 현재 `Write`와 `Edit` 도구만 검사한다.
- Java 도메인 운영 코드 경로만 검사하며 리소스 XML이나 다른 방식의 파일 변경까지 완전하게 통제하는 보안 장치는 아니다.
- 목적은 악의적인 우회 방지가 아니라 스펙 확정을 잊지 않게 하는 것이다.

## 8. Hook 2 — `merge-gate-arm.ps1`

### 실행 시점

Claude가 Bash 명령을 실행한 직후인 `PostToolUse`에서 실행된다.

### 감지하는 명령

- `gh pr merge`
- `git merge`

다음 명령은 오탐을 막기 위해 제외한다.

- `git merge-base`
- `git merge-tree`
- `git merge-file`
- `git merge --abort`
- `git merge --quit`

### 감지 후 동작

`.claude/state/merge-gate-pending.json`에 다음 정보를 기록한다.

```json
{
  "armedAt": "검증 대기 시작 시각",
  "sessionId": "머지를 실행한 Claude 세션",
  "command": "감지된 머지 명령",
  "headAtArm": "당시 로컬 HEAD"
}
```

그리고 이전 차단 횟수 파일을 초기화한 뒤 다음 행동을 컨텍스트로 안내한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1
```

### 안전 특성

- 상태 기록 실패가 전체 작업을 막지 않도록 fail-open으로 동작한다.
- 새 머지를 감지하면 이전 pending 정보를 덮어쓰고 새 검증 사이클을 시작한다.

## 9. Hook 3 — `merge-gate-stop.ps1`

### 실행 시점

Claude가 응답을 끝내려는 `Stop` 시점에 실행된다.

### 판정 방식

1. `merge-gate-pending.json`이 없으면 종료를 허용한다.
2. pending을 만든 세션과 현재 세션이 다르면 종료를 허용한다.
3. 같은 세션에 미검증 pending이 있으면 종료를 차단한다.
4. 차단 메시지에서 검증 실행, 실패 수정, 사유 있는 `-Skip` 중 하나를 요구한다.

### 최대 차단 횟수

종료 차단은 최대 3회다. 세션이 영구적으로 갇히는 것을 막기 위한 안전 상한이다.

3회 차단 후 다시 종료하면:

- 종료 자체는 허용한다.
- `.claude/state/merge-gate.log`에 `ESCALATED`를 기록한다.
- pending 상태는 그대로 유지한다.

따라서 다음 작업 전에 정상 검증 또는 사유 있는 `-Skip`으로 정리해야 한다.

### 세션 분리

pending에 기록된 `sessionId`가 현재 Stop 세션과 다르면 차단하지 않는다. 같은 PC에서 다른 Claude 세션을 열었을 때 남의 검증 대기 상태 때문에 작업이 막히지 않도록 한 설계다.

## 10. 머지 검증 스크립트 — `verify-merge.ps1`

### 기본 실행

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1
```

기본값:

- 부팅 제한 시간: 120초
- 검증 서버 포트: 18080
- Spring 프로필: `local`

개발 서버의 기본 포트 8080과 충돌하지 않도록 별도 포트를 사용한다.

### 1단계: 병합 잔재와 V번호 중복

- Git의 unmerged path 확인
- `<<<<<<<`, `>>>>>>>` 충돌 표시 확인
- `docs/sql/V*.sql`의 동일 V번호 중복 확인

서로 다른 브랜치가 `V6_x.sql`, `V6_y.sql`을 각각 추가하면 Git은 파일명이 달라 충돌로 보지 않는다. 이 검사는 파일명이 아니라 V번호를 기준으로 중복을 찾는다.

### 2단계: 로컬 DB 마이그레이션 반영 확인

- `.env`의 `LOCAL_DB_*` 설정 사용
- 모든 증분 SQL(V2 이상)의 결과가 로컬 MariaDB에 반영됐는지 확인
- 테이블과 컬럼 생성·삭제
- 상태 CHECK 제약과 허용값
- 주요 컬럼 정의
- STORED 생성 열 표현식

특정 머지 커밋만 기준으로 삼지 않고 모든 증분 V파일의 결과를 매번 확인한다. GitHub에서 이루어진 머지, squash/rebase 머지, 검증 실패 후 추가 수정에도 일관되게 동작하기 위한 선택이다.

### 3단계: 전체 테스트

```powershell
.\gradlew.bat test
```

컴파일 오류, 삭제된 클래스 참조, 두 기능을 합쳤을 때 생기는 의미상 충돌을 확인한다. Gradle 산출물 잠금 문제가 감지되면 정리 후 한 번 재시도한다.

### 4단계: 부팅과 화면 스모크

검증 서버를 18080 포트로 실행하고 다음을 확인한다.

- `/actuator/health`로 부팅 완료 확인
- 공개 화면: `/`, `/screens`, `/login`, `/signup`, `/community`, `/products`, `/cart`
- 관리자 로그인 후: `/admin`, `/admin/store`, `/admin/products`, `/admin/products/new`, `/admin/community`, `/admin/orders`, `/admin/members`, `/mypage`

응답 상태가 200인지뿐 아니라 다음도 검사한다.

- HTML 본문이 `</html>`까지 완성됐는지
- 404, 500, 요청 오류 페이지가 아닌지
- 관리자 화면에서 로그인 세션이 유지되는지

렌더링 도중 예외가 나도 부분 응답이 200으로 반환될 수 있기 때문에 본문 완결성까지 확인한다.

### 종료 처리

- 검증 서버 프로세스와 자식 프로세스를 종료한다.
- 검증 포트가 실제로 해제됐는지 확인한다.
- Gradle daemon을 정리한다.
- 부팅 로그를 `.claude/state/verify-boot.log`에 남긴다.

### 결과 처리

| 결과 | 동작 |
|---|---|
| 통과 | pending 삭제, 로그에 `VERIFIED`, 종료 코드 0 |
| 실패 | pending 유지, 로그에 `FAILED`, 종료 코드 1 |
| `-Skip "사유"` | pending 삭제, 로그에 `SKIPPED`와 사유, 종료 코드 0 |

## 11. 로컬 마이그레이션 실행기 — `apply-local-migration.ps1`

### 목적

Flyway를 사용하지 않는 환경에서 `docs/sql`의 증분 SQL 한 파일을 안전하게 로컬 MariaDB에 적용한다.

### 실행

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\V6_example.sql
```

실제 적용 없이 경로·설정·클라이언트만 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\apply-local-migration.ps1 -File docs\sql\V6_example.sql -ValidateOnly
```

### 보호 장치

- `docs/sql` 밖의 파일은 거부한다.
- `V<번호>_<이름>.sql` 규칙에 맞지 않는 파일은 거부한다.
- 프로젝트 루트 `.env`의 `LOCAL_DB_*` 값을 사용한다.
- MariaDB CLI가 설치되어 있는지 확인한다.
- DB 비밀번호를 명령행 인수에 넣지 않고 일시적인 `MYSQL_PWD` 환경 변수로 전달한다.
- 실행 후 기존 환경 변수를 복원한다.

## 12. 로컬 상태 파일

| 파일 | 의미 | 삭제 시점 |
|---|---|---|
| `merge-gate-pending.json` | 머지는 됐지만 검증은 끝나지 않은 상태 | 검증 통과 또는 사유 있는 `-Skip` |
| `merge-gate-blocks.txt` | 현재 검증 사이클의 Stop 차단 횟수 | 새 머지 감지 또는 pending 해제 후 Stop |
| `merge-gate.log` | `VERIFIED`, `FAILED`, `SKIPPED`, `ESCALATED` 이력 | 자동 삭제하지 않음 |
| `verify-boot.log` | 검증용 Spring Boot 실행 로그 | 다음 검증에서 갱신 |

`.claude/state/`는 Git 추적 대상이 아니다. 각 개발자의 로컬 상태만 보관하므로 팀원에게 전파되지 않는다.

## 13. 정상 사용 시나리오

### 새 도메인을 시작할 때

```text
1. /new-domain order
2. docs/specs/order.md 초안 작성
3. 사용자와 스펙 확정
4. status: approved 변경
5. 기능 구현과 테스트
6. feature/order push
7. dev 대상 PR 생성
```

### 미승인 스펙으로 코드 변경이 차단됐을 때

```text
1. 훅이 알려준 도메인명 확인
2. /new-domain <도메인명> 실행
3. 스펙 작성과 사용자 확정
4. status: approved 확인
5. 다시 구현
```

### PR을 머지할 때

```text
1. 사용자가 명시적으로 머지 지시
2. /merge-feature <PR 번호>
3. PR·CI·V번호·공통 파일 점검
4. dev에 머지하고 로컬 dev 동기화
5. scripts/verify-merge.ps1 실행
6. VERIFIED 확인
7. 브랜치 정리와 후속 작업 보고
```

### 머지 검증에서 DB 반영 누락이 발견됐을 때

```text
1. 실패한 V파일 확인
2. apply-local-migration.ps1 -ValidateOnly 실행
3. 해당 V파일을 로컬 DB에 적용
4. verify-merge.ps1 재실행
```

## 14. 설계상 한계

- GitHub 웹에서 머지하면 `PostToolUse(Bash)` 훅이 실행되지 않아 게이트가 자동 무장되지 않는다.
- 머지 명령 문자열이 Bash 명령 안에 단순히 포함돼도 게이트가 무장될 수 있다.
- 상태 파일을 직접 삭제하거나 Hook 대상이 아닌 방식으로 파일을 수정하면 우회할 수 있다.
- 스펙 관문은 현재 Java 도메인 경로의 `Write`·`Edit`에 집중되어 있다.
- 머지 검증은 로컬 MariaDB와 MariaDB CLI가 필요하다.
- SQL 전체 문법을 해석하는 마이그레이션 엔진은 아니다. 새로운 DDL 패턴을 도입하면 검증 로직도 확장해야 한다.
- Hook은 작업 실수를 줄이는 협업 안전장치이지 보안 또는 변조 방지 시스템은 아니다.

## 15. 유지보수 체크리스트

### `CLAUDE.md`를 바꿀 때

- 절대 규칙과 실제 코드·브랜치 정책이 일치하는지 확인한다.
- 상세 정본인 `docs/conventions.md`, `docs/team-plan.md`와 중복·충돌하지 않는지 확인한다.
- 너무 긴 세부 절차는 Skill이나 별도 문서로 분리한다.

### Skill을 추가하거나 바꿀 때

- 언제 호출해야 하는지 description에 명확히 적는다.
- 입력 인자가 있으면 `argument-hint`를 적는다.
- 사전 조건, 실행 순서, 실패 시 복구, 완료 기준을 포함한다.
- 관련 Hook이 있으면 어느 단계에서 연결되는지 설명한다.

### Hook을 추가하거나 바꿀 때

- `.claude/settings.json`의 이벤트와 matcher를 확인한다.
- 훅은 빠른 판정만 하고 무거운 작업은 별도 스크립트로 분리한다.
- 입력 JSON 파싱 실패와 내부 오류의 처리 방식을 명확히 정한다.
- 영구 차단을 피할 안전 상한이나 복구 경로를 둔다.
- PowerShell 5.1과 Claude Code 사이의 한글 인코딩을 고려한다.
- 한글 리터럴이 있는 PowerShell 훅 파일은 UTF-8 BOM으로 저장한다.

### 머지 검증 범위를 바꿀 때

- 새 공개 화면 또는 관리자 화면을 스모크 목록에 추가한다.
- 새 SQL 문법을 사용하면 DB 구조 대조 로직을 확장한다.
- 검증 서버 종료 후 포트와 Gradle 프로세스가 남지 않는지 확인한다.
- 성공·실패·예외 해제 로그가 모두 남는지 확인한다.

## 16. 현재까지의 주요 변경 기록

| 날짜 | 내용 |
|---|---|
| 2026-07-24 | 프로젝트 기본 `CLAUDE.md`와 초기 도메인 기준 정리 |
| 2026-07-25 | `/new-domain` 스펙 우선 절차와 브랜치·PR 단계 보강 |
| 2026-07-25 | 머지 후 검증 게이트, `/merge-feature`, 통합 검증 스크립트 추가 |
| 2026-07-25 | 로컬 상태 디렉터리를 Git 추적 대상에서 제외 |
| 2026-07-25 | SQL 재적용과 개발 워크플로·검증 범위 보강 |
| 2026-07-26 | MariaDB 정수 타입 구조 검증의 오탐 수정 |

## 17. 핵심 요약

- 구현 전에는 스펙을 확정한다.
- 승인 여부는 `guard-new-domain` 훅이 자동 검사한다.
- 구현 절차는 `/new-domain`이 안내한다.
- 기능 작업은 `dev` 대상 PR 생성까지가 기본 범위다.
- 머지는 사용자 지시가 있을 때만 한다.
- 머지하면 `merge-gate-arm`이 검증 대기 상태를 만든다.
- 통합 검증은 `verify-merge.ps1`이 수행한다.
- 미검증 종료는 `merge-gate-stop`이 최대 3회 막는다.
- 예외 해제는 상태 파일 삭제가 아니라 `-Skip "사유"`를 사용한다.
- 자동화의 목적은 팀 규칙을 강제로 숨기는 것이 아니라, 중요한 절차를 눈에 보이게 만들고 누락을 줄이는 것이다.
