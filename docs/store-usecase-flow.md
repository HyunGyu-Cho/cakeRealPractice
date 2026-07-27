# store 수직 슬라이스 — 표준 예시 코드 흐름

`domain/store`는 팀 표준 수직 슬라이스다. **판단이 서지 않으면 store 코드를 그대로 따른다.**
이 문서는 그 코드가 어떤 파일과 메서드를 거쳐 동작하는지 흐름만 보여준다.

> 규칙의 정본은 [`conventions.md`](conventions.md), **동작의 정본은 store 소스 코드**다.
> 이 문서는 흐름 지도이지 코드 사본이 아니다 — 세부는 항상 코드를 본다.

---

## 1. 전체 구조

```text
관리자 브라우저
  → Spring Security          (/admin/** 권한·CSRF)
  → StoreAdminController     (요청 수신, 검증 결과 처리)
  → StoreUpdateForm          (@Valid 입력 검증)
  → StoreService             (업무 로직 + 트랜잭션)
      ├─ ImageValidator      (업로드 검증)
      ├─ FileStorageClient   (파일 저장·삭제)
      └─ StoreMapper → StoreMapper.xml → MariaDB
  → redirect:/admin/store    (PRG)
  → Thymeleaf 렌더링
```

## 2. 파일과 역할

| 계층 | 파일 | 역할 |
|---|---|---|
| 화면 | `templates/admin/store/form.html` | 매장 정보 조회·수정 폼 |
| 보안 | `global/security/SecurityConfig.java` | `/admin/**` 관리자 권한 검사 |
| Controller | `store/controller/StoreAdminController.java` | HTTP 수신, 검증 결과 처리, 화면 반환 |
| 입력 DTO | `store/dto/form/StoreUpdateForm.java` | 폼 바인딩 + Bean Validation |
| 조회 DTO | `store/dto/view/StoreView.java` | 여러 테이블 결과를 화면 형태로 조합(불변 record) |
| Service | `store/service/StoreService.java` | 업무 로직·트랜잭션 |
| Entity | `store/entity/Store.java`, `StoreBusinessHour.java` | 테이블 한 행 (순수 POJO) |
| Mapper | `store/mapper/StoreMapper.java` + `mapper/store/StoreMapper.xml` | SELECT / UPDATE / UPSERT |
| 파일 | `global/infra/ImageValidator`, `LocalFileStorageClient`, `StoredFileCleanup` | 업로드 검증·저장·보상 삭제 |
| 공개 | `global/config/WebConfig.java` | `/uploads/**` ↔ 업로드 폴더 연결 |
| 오류 | `store/error/StoreErrorCode.java`, `global/error/GlobalExceptionHandler.java` | 도메인 오류 코드 / 오류 화면 변환 |

---

## 3. 호출 흐름 4가지

### 3.1 조회

```text
GET /admin/store
  → SecurityConfig (ADMIN 권한)
  → StoreAdminController.form()
  → StoreService.getStoreView()
      → StoreMapper.findStoreById(1) / findBusinessHours(1) / findHolidays(1)
      → StoreView 조합
  → StoreUpdateForm.from(StoreView)     ← 조회 결과로 폼 초기값을 채운다
  → admin/store/form.html
```

### 3.2 수정 성공

```text
POST /admin/store
  → SecurityConfig (ADMIN 권한 + CSRF)
  → StoreAdminController.update()
  → StoreUpdateForm 바인딩 → @Valid 통과
  → StoreService.updateStore()
      → 트랜잭션 시작
      → findStoreById(1) → Entity 값 변경
      → 새 이미지가 있으면 ImageValidator 검증 후 FileStorageClient.store()
      → updateStore() + upsertBusinessHour() 7회      ← 한 트랜잭션
      → 커밋
      → 커밋 후 이전 이미지 삭제
  → successMessage 플래시 저장 → redirect:/admin/store
```

### 3.3 입력 검증 실패

```text
POST /admin/store
  → @Valid 실패
  → Service를 호출하지 않는다
  → 리다이렉트 없이 form.html 재렌더 (사용자 입력 유지)
```

### 3.4 업무 오류

```text
POST /admin/store
  → StoreService에서 BusinessException(StoreErrorCode.…)
  → 트랜잭션 롤백
  → GlobalExceptionHandler → error/4xx · error/500
```

> 화면에서 고칠 수 있는 오류(중복 휴무일 등)는 예외 대신
> `bindingResult.rejectValue(...)`로 해당 필드에 돌려준다 — `addHoliday` 참고.

---

## 4. 이 슬라이스가 보여주는 판단 3가지

### 4.1 파일과 DB 트랜잭션의 보상 처리

파일 시스템은 DB 트랜잭션에 참여하지 않는다. `StoredFileCleanup`이
`TransactionSynchronization.afterCompletion()`으로 DB 결과에 맞춰 정리한다.

```text
커밋  → 새 이미지 URL 유지 + 기존 이미지 파일 삭제
롤백  → 기존 이미지 URL·파일 유지 + 새 이미지 파일 보상 삭제
```

파일 정리 실패는 이미 확정된 DB 결과를 뒤집지 않도록 **경고 로그로만** 남긴다.

### 4.2 업로드 검증은 global이 소유한다

`ImageValidator`가 형식 화이트리스트 + 크기 + **실제 파일 머리 바이트**를 검사한다.
Content-Type은 클라이언트가 조작할 수 있어 그것만으로는 검증이 되지 않는다.
검증기는 예외를 던지지 않고 위반 사유를 돌려주므로, 도메인이 받아서 자기 `ErrorCode`로 감싼다.

### 4.3 단일 매장 ID 하드코딩

```java
public static final long DEFAULT_STORE_ID = 1L;
```

단일 매장 MVP에는 적절하지만 다중 지점은 지원하지 못한다. 확장한다면 로그인한 관리자가
관리할 수 있는 `storeId`를 구하고 권한을 검증하는 단계가 필요하다.

> 이 상수 때문에 대표 매장 1행과 7개 요일 영업시간이 **필수 시드**다([`database.md` 8장](database.md#8-시드-구분-로컬-vs-공용-rds)).

---

## 5. 테스트가 고정하는 것

**`StoreServiceTests`** — 업무 규칙

- 매장 기본정보와 7개 요일 영업시간이 한 트랜잭션에서 함께 수정되는지
- 휴무일의 시작·종료 시간이 `null`로 저장되는지
- 새 이미지를 저장하고 기존 이미지를 삭제하는지
- DB 실패·롤백 시 새 이미지를 보상 삭제하고 기존 이미지를 유지하는지
- 이미지가 아닌 업로드와 중복 휴무일을 거부하는지

**`StoreAdminControllerTests`** — 검증과 PRG

- `GET`이 필요한 Model 데이터를 만드는지
- 잘못된 입력에서 **Service를 호출하지 않는지**
- 정상 수정 후 `/admin/store`로 리다이렉트하고 `successMessage`를 Flash에 담는지

> 실구현 도메인은 이 두 축(Service 업무 규칙 + Controller 검증/PRG)을 표준으로 삼는다.
