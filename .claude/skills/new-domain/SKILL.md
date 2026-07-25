---
name: new-domain
description: 도메인 구현 착수 표준 절차 (스펙 우선). 새 도메인을 구현하거나 목업 도메인을 실구현으로 전환하기 시작할 때, 또는 스펙 없는 도메인의 코드를 만들려다 훅에 차단됐을 때 사용한다.
argument-hint: "[도메인명]"
---

# 도메인 구현 착수 절차 (spec-driven)

대상 도메인: $ARGUMENTS (비어 있으면 사용자에게 확인)

CLAUDE.md 절대규칙과 docs/conventions.md를 전제로 한다. 표준 구현체는 domain/store(설정형)·domain/product(목록·페이징 CRUD)다.

## 0단계. 스펙 작성·확정 (필수 — 이게 없으면 훅이 새 클래스 생성을 차단한다)

1. `docs/specs/<도메인>.md`가 있는지 확인한다. 있으면 읽고 4단계로 간다.
2. 없으면 `docs/specs/_template.md`를 복사해 작성한다. 이때 반드시:
   - 해당 화면의 목업 템플릿과 mockup JS(`static/js/customer-mockup.js` 등)를 읽고 임시 동작을 추출한다.
   - `docs/team-plan.md` 8장에서 이 도메인 관련 미결 항목을 찾아 확정안을 제안한다.
   - 상태값이 필요하면 conventions.md 상태값 공통 규칙(영문 enum 이름·VARCHAR(20)·CHECK·전이는 service)대로 정의한다.
3. **작성한 스펙을 사용자에게 보여주고 확정을 받는다.** 확정 전에는 구현 코드를 만들지 않는다.
4. 확정되면: team-plan 8장 표·conventions.md 인벤토리 등 관련 정본 문서를 갱신한다.

## 1단계. DB

- 스키마 변경이 필요하면 새 증분 V파일을 만들고 **V0_ERD.sql·V1_first_MVC_table.sql에도 소급 반영**한다(절대규칙).
- 상태 컬럼은 `VARCHAR(20) NOT NULL` + `chk_<table>_status CHECK` + 시작 상태 DEFAULT(시작 상태가 여럿이면 DEFAULT 생략하고 서비스가 세팅).

## 2단계. 백엔드 구현 (store 슬라이스 순서)

entity(순수 POJO+Lombok) → Mapper 인터페이스+XML(`#{}`만, 컬럼 명시, 단건 Optional) → ErrorCode enum(`<도메인>_001` 접두어) → Service(`@Transactional`, BusinessException, 상태 전이 검증) → dto/form(Bean Validation)·dto/view(record) → Controller(PRG, `successMessage`/`errorMessage` 문자열 키, 검증 실패 재렌더).

## 3단계. 화면 연결

- URL·템플릿은 유지하고 하드코딩 본문만 `th:each`/`th:text` + Model 데이터로 교체한다.
- 해당 화면의 mockup JS/CSS 의존(`customer-mockup.js`, `data-mock-form` 등)을 제거한다. 순수 UI 인터랙션은 유지.
- 프론트 원본 기반 고객 목업 16개는 import 스크립트가 덮어쓰므로, 실구현 전환 시 그 목록에서 빠지는지 확인한다.

## 4단계. 테스트

- Service 테스트(`StoreServiceTests` 패턴) + Controller 테스트(`StoreAdminControllerTests` 패턴, standaloneSetup+mock).
- 실구현으로 전환된 화면은 목업 스모크 테스트(`AdminPageControllerTests`·`CustomerPageControllerTests`)에서 제외한다.

## 5단계. 검증·마무리

- `.\gradlew.bat test` 전체 통과 확인.
- 로컬 실행 후 해당 화면 동작 확인(`--spring.profiles.active=local`).
- 스펙의 "완료 기준" 체크리스트를 점검하고, 스펙 문서를 실제 구현과 일치하도록 최종 갱신한다.
- TodoList.md 해당 항목을 `[x]`로, README 화면 현황표를 갱신한다.

## 6단계. 브랜치·PR (기능 하나 = 브랜치 하나 = PR 하나)

- 작업 시작 시 `feature/<도메인명>` 브랜치를 만들지 않았다면 이 시점에 만들어 변경을 옮긴다.
- 커밋 메시지는 한글로, 성격이 다른 변경은 커밋을 나눈다.
- push 후 `gh pr create --base dev`로 **dev 대상** PR을 만든다(main 직접 push 금지).
- PR 본문에 SQL 적용 순서와 검증 결과(테스트·E2E)를 적고, `global/*` 변경이 있으면 "합의 필요" 절로 명시한다.
