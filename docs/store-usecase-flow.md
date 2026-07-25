# 매장 정보 관리 유스케이스 코드 흐름

## 1. 문서 목적

이 문서는 현재 프로젝트에서 **관리자가 매장 정보와 영업시간을 조회하고 수정하는 유스케이스**가 어떤 파일과 메서드를 거쳐 처리되는지 설명한다.

분석 대상은 `store` 도메인이며, 전체적인 구조는 다음과 같다.

```text
관리자 브라우저
  → Spring Security
  → StoreAdminController
  → StoreUpdateForm 검증
  → StoreService
      ├─ FileStorageClient
      └─ StoreMapper
           └─ StoreMapper.xml
                └─ MariaDB
  → redirect:/admin/store
  → 수정된 정보 재조회
  → Thymeleaf 화면 렌더링
```

## 2. 주요 파일과 역할

| 계층 | 파일 | 역할 |
|---|---|---|
| 화면 | `templates/admin/store/form.html` | 매장 정보 조회·수정 폼 |
| 보안 | `global/security/SecurityConfig.java` | `/admin/**` 관리자 권한 검사 |
| Controller | `store/controller/StoreAdminController.java` | HTTP 요청 수신, 검증 결과 처리, 화면 반환 |
| 입력 DTO | `store/dto/form/StoreUpdateForm.java` | 폼 데이터 바인딩과 입력값 검증 |
| 조회 DTO | `store/dto/view/StoreView.java` | 여러 테이블의 조회 결과를 관리자 화면 형태로 조합 |
| Service | `store/service/StoreService.java` | 매장 정보 조회·수정 비즈니스 로직 및 트랜잭션 관리 |
| Entity | `store/entity/Store.java` | `store` 테이블 한 행 표현 |
| Entity | `store/entity/StoreBusinessHour.java` | 요일별 영업시간 한 행 표현 |
| Mapper | `store/mapper/StoreMapper.java` | Service가 사용하는 MyBatis Mapper 인터페이스 |
| SQL Mapper | `mapper/store/StoreMapper.xml` | 실제 SELECT, UPDATE, UPSERT SQL |
| 파일 저장 | `global/infra/LocalFileStorageClient.java` | 매장 이미지의 로컬 파일 저장·삭제 |
| 파일 공개 | `global/config/WebConfig.java` | `/uploads/**` URL과 실제 업로드 폴더 연결 |
| 예외 처리 | `global/error/GlobalExceptionHandler.java` | 처리되지 않은 업무 예외를 오류 화면으로 변환 |
| 오류 코드 | `store/error/StoreErrorCode.java` | 매장 도메인의 오류 코드·메시지·HTTP 상태 정의 |

---

## 3. 유스케이스 A: 매장 관리 화면 조회

관리자가 다음 URL에 접근하는 상황이다.

```http
GET /admin/store
```

### 3.1 관리자 권한 검사

요청은 Controller에 도착하기 전에 `SecurityConfig`를 통과한다.

파일:

```text
src/main/java/com/cakeshop/global/security/SecurityConfig.java
```

관련 코드:

```java
auth.requestMatchers("/admin/**").hasRole("ADMIN");
```

따라서 `/admin/store`는 `ADMIN` 역할을 가진 로그인 사용자만 접근할 수 있다.

권한이 없으면 Controller가 실행되지 않고 Spring Security 또는 전역 예외 처리 흐름으로 넘어간다.

### 3.2 StoreAdminController.form()

권한 검사를 통과하면 다음 Controller 메서드가 실행된다.

파일:

```text
src/main/java/com/cakeshop/domain/store/controller/StoreAdminController.java
```

관련 코드:

```java
@GetMapping
public String form(Model model) {
    StoreView store = storeService.getStoreView();
    model.addAttribute("storeForm", StoreUpdateForm.from(store));
    model.addAttribute("holidayForm", new StoreHolidayForm());
    addReferenceData(model, store);
    return "admin/store/form";
}
```

처리 순서는 다음과 같다.

1. `storeService.getStoreView()`로 매장 정보를 조회한다.
2. 조회 결과인 `StoreView`를 입력용 `StoreUpdateForm`으로 변환한다.
3. 매장 수정 폼, 특정 휴무일 폼, 요일 목록, 휴무일 목록, 현재 이미지 URL을 `Model`에 넣는다.
4. `admin/store/form` Thymeleaf 화면을 반환한다.

Controller는 DB에 직접 접근하지 않고 Service에 조회를 위임한다.

### 3.3 StoreService.getStoreView()

파일:

```text
src/main/java/com/cakeshop/domain/store/service/StoreService.java
```

관련 코드:

```java
@Transactional(readOnly = true)
public StoreView getStoreView() {
    Store store = findDefaultStore();
    List<StoreBusinessHour> hours =
        storeMapper.findBusinessHours(DEFAULT_STORE_ID);
    List<StoreHoliday> holidays =
        storeMapper.findHolidays(DEFAULT_STORE_ID);

    // 조회 결과 조합
    ...

    return new StoreView(...);
}
```

현재 서비스는 단일 매장을 전제로 한다.

```java
public static final long DEFAULT_STORE_ID = 1L;
```

따라서 모든 매장 조회와 수정은 ID가 `1`인 대표 매장을 대상으로 한다.

Service는 다음 데이터를 각각 조회한다.

```text
store
  → 매장명, 설명, 이미지, 주소, 연락처, 픽업 정보

store_business_hour
  → 월요일부터 일요일까지의 영업시간과 휴무 여부

store_holiday
  → 오늘 이후의 특정 휴무일
```

### 3.4 StoreMapper 호출

Service는 다음 Mapper 인터페이스를 호출한다.

파일:

```text
src/main/java/com/cakeshop/domain/store/mapper/StoreMapper.java
```

```java
Optional<Store> findStoreById(Long storeId);

List<StoreBusinessHour> findBusinessHours(Long storeId);

List<StoreHoliday> findHolidays(Long storeId);
```

MyBatis는 메서드 이름과 `StoreMapper.xml`의 SQL ID를 연결한다.

파일:

```text
src/main/resources/mapper/store/StoreMapper.xml
```

매장 기본정보 조회:

```sql
SELECT id,
       name,
       description,
       image_url,
       address,
       phone,
       pickup_place,
       pickup_start_time,
       pickup_end_time,
       pickup_interval_minutes,
       created_at,
       updated_at
  FROM store
 WHERE id = #{storeId}
```

요일별 영업시간 조회:

```sql
SELECT id,
       store_id,
       day_of_week,
       open_time,
       close_time,
       is_closed
  FROM store_business_hour
 WHERE store_id = #{storeId}
```

향후 특정 휴무일 조회:

```sql
SELECT id,
       store_id,
       holiday_date,
       reason
  FROM store_holiday
 WHERE store_id = #{storeId}
   AND holiday_date >= CURRENT_DATE
 ORDER BY holiday_date
```

### 3.5 StoreView 생성

`StoreService`는 조회한 세 종류의 데이터를 하나의 `StoreView`로 조합한다.

```text
Store
         ┐
영업시간  ├─→ StoreView
휴무일    ┘
```

`StoreView`는 관리자 화면 조회에 필요한 정보를 표현하는 읽기 전용 DTO다.

파일:

```text
src/main/java/com/cakeshop/domain/store/dto/view/StoreView.java
```

```java
public record StoreView(
    Long id,
    String name,
    String description,
    String imageUrl,
    String address,
    String phone,
    LocalTime weekdayOpenTime,
    LocalTime weekdayCloseTime,
    LocalTime weekendOpenTime,
    LocalTime weekendCloseTime,
    Set<DayOfWeek> closedDays,
    String pickupPlace,
    LocalTime pickupStartTime,
    LocalTime pickupEndTime,
    Integer pickupIntervalMinutes,
    List<StoreHoliday> holidays
) {
}
```

요일별 DB 데이터는 화면에서 사용하는 평일·주말 시간과 정기 휴무일 집합으로 변환된다.

예를 들어 월요일이 휴무라면 평일 영업시간은 화요일부터 금요일 중 처음으로 휴무가 아닌 요일의 시간을 대표값으로 사용한다.

### 3.6 StoreUpdateForm으로 변환

Controller는 `StoreView`를 직접 수정 폼으로 사용하지 않는다.

```java
StoreUpdateForm.from(store)
```

파일:

```text
src/main/java/com/cakeshop/domain/store/dto/form/StoreUpdateForm.java
```

역할은 다음과 같이 구분된다.

| 객체 | 역할 |
|---|---|
| `Store` | DB의 `store` 한 행 |
| `StoreBusinessHour` | DB의 요일별 영업시간 한 행 |
| `StoreView` | 여러 테이블을 조합한 관리자 조회 결과 |
| `StoreUpdateForm` | 관리자 입력값과 입력 검증 |

이렇게 분리하면 화면 검증 규칙이 DB Entity에 직접 섞이지 않는다.

### 3.7 Thymeleaf 화면 렌더링

Controller는 다음 View 이름을 반환한다.

```java
return "admin/store/form";
```

실제 파일:

```text
src/main/resources/templates/admin/store/form.html
```

폼은 `storeForm`을 바인딩한다.

```html
<form th:action="@{/admin/store}"
      th:object="${storeForm}"
      method="post"
      enctype="multipart/form-data">
```

이 단계까지의 전체 호출 경로는 다음과 같다.

```text
GET /admin/store
  → SecurityConfig
  → StoreAdminController.form()
  → StoreService.getStoreView()
  → StoreMapper.findStoreById(1)
  → StoreMapper.findBusinessHours(1)
  → StoreMapper.findHolidays(1)
  → StoreMapper.xml의 SELECT SQL
  → StoreView 생성
  → StoreUpdateForm.from(StoreView)
  → Model에 화면 데이터 저장
  → templates/admin/store/form.html
```

---

## 4. 유스케이스 B: 매장 정보 수정

관리자가 값을 변경하고 **매장 정보 저장** 버튼을 누르는 상황이다.

```http
POST /admin/store
Content-Type: multipart/form-data
```

### 4.1 화면에서 전송되는 데이터

`form.html`은 다음 정보를 전송한다.

- 매장명
- 매장 소개
- 주소
- 연락처
- 새 매장 이미지
- 평일 시작·종료 시간
- 주말 시작·종료 시간
- 정기 휴무 요일
- 픽업 장소
- 픽업 시작·종료 시간
- 픽업 시간 간격

이미지는 `StoreUpdateForm`에 포함되지 않고 별도의 `MultipartFile`로 전달된다.

```html
<input id="image"
       type="file"
       name="image"
       accept="image/*">
```

### 4.2 StoreAdminController.update()

파일:

```text
src/main/java/com/cakeshop/domain/store/controller/StoreAdminController.java
```

```java
@PostMapping
public String update(
        @Valid @ModelAttribute("storeForm") StoreUpdateForm form,
        BindingResult bindingResult,
        @RequestParam(name = "image", required = false)
        MultipartFile image,
        Model model,
        RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {
        model.addAttribute("holidayForm", new StoreHolidayForm());
        addReferenceData(model, storeService.getStoreView());
        return "admin/store/form";
    }

    storeService.updateStore(form, image);
    redirectAttributes.addFlashAttribute(
        "successMessage",
        "매장 정보를 저장했습니다."
    );

    return "redirect:/admin/store";
}
```

처리 순서는 다음과 같다.

1. 요청 파라미터를 `StoreUpdateForm`에 바인딩한다.
2. `@Valid`로 입력값을 검증한다.
3. 이미지 파일은 `MultipartFile`로 별도 바인딩한다.
4. 입력 오류가 있으면 Service를 호출하지 않고 기존 화면을 다시 렌더링한다.
5. 검증에 성공하면 `StoreService.updateStore()`를 호출한다.
6. 성공 메시지를 Flash Attribute에 넣는다.
7. `GET /admin/store`로 리다이렉트한다.

### 4.3 StoreUpdateForm 검증

`StoreUpdateForm`에는 다음과 같은 검증 규칙이 있다.

```java
@NotBlank
@Size(max = 100)
private String name;

@NotBlank
@Size(max = 255)
private String address;

@NotBlank
@Pattern(regexp = "^[0-9+() -]{8,30}$")
private String phone;
```

시간의 선후 관계는 객체 수준에서 검사한다.

```java
@AssertTrue(message = "평일 영업 종료 시간은 시작 시간보다 늦어야 합니다.")
public boolean isWeekdayTimeRangeValid() {
    return isIncreasing(weekdayOpenTime, weekdayCloseTime);
}
```

따라서 다음과 같은 입력은 Controller 단계에서 차단된다.

```text
매장명 없음
주소 없음
잘못된 연락처
영업 종료 시간 ≤ 영업 시작 시간
픽업 종료 시간 ≤ 픽업 시작 시간
픽업 간격이 10분 미만 또는 180분 초과
```

검증 오류가 있으면 `BindingResult.hasErrors()`가 `true`가 되고, 같은 폼 화면에 오류 메시지가 출력된다.

### 4.4 StoreService.updateStore()

파일:

```text
src/main/java/com/cakeshop/domain/store/service/StoreService.java
```

```java
@Transactional
public void updateStore(StoreUpdateForm form, MultipartFile image)
```

이 메서드의 DB 작업은 하나의 트랜잭션으로 처리된다.

정상 흐름:

```text
기존 매장 조회
  → 입력값을 Store에 반영
  → 새 이미지가 있으면 저장
  → store 테이블 UPDATE
  → 기존 이미지 삭제
  → 월~일 영업시간 UPSERT 7회
  → 트랜잭션 커밋
```

### 4.5 기존 매장 조회

Service는 먼저 ID가 `1`인 매장을 조회한다.

```java
Store store = findDefaultStore();
```

내부 호출 경로:

```text
StoreService.findDefaultStore()
  → StoreMapper.findStoreById(1)
  → StoreMapper.xml의 findStoreById
  → SELECT ... FROM store WHERE id = 1
```

매장이 없으면 다음 예외가 발생한다.

```java
throw new BusinessException(StoreErrorCode.NOT_FOUND);
```

### 4.6 입력값을 Store Entity에 반영

```java
store.setName(form.getName().trim());
store.setDescription(trimToNull(form.getDescription()));
store.setAddress(form.getAddress().trim());
store.setPhone(form.getPhone().trim());
store.setPickupPlace(form.getPickupPlace().trim());
store.setPickupStartTime(form.getPickupStartTime());
store.setPickupEndTime(form.getPickupEndTime());
store.setPickupIntervalMinutes(form.getPickupIntervalMinutes());
```

필수 문자열은 앞뒤 공백을 제거한다.

매장 소개처럼 선택값인 문자열은 비어 있으면 `null`로 변환한다.

### 4.7 이미지 저장

새 이미지가 전달된 경우에만 기존 이미지를 교체한다.

```java
String previousImageUrl = store.getImageUrl();
boolean imageReplaced = image != null && !image.isEmpty();

if (imageReplaced) {
    validateImage(image);
    store.setImageUrl(fileStorageClient.store(image, "store"));
}
```

이미지가 없으면 기존 `imageUrl`을 그대로 유지한다.

`validateImage()`는 업로드된 파일의 Content-Type이 `image/*`인지 확인한다.

```java
private void validateImage(MultipartFile image) {
    String contentType = image.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        throw new BusinessException(StoreErrorCode.INVALID_IMAGE);
    }
}
```

실제 파일 저장 구현:

```text
src/main/java/com/cakeshop/global/infra/LocalFileStorageClient.java
```

저장 규칙:

```text
실제 파일
{app.file.upload-dir}/store/{yyyyMM}/{UUID}.{확장자}

DB에 저장되는 URL
/uploads/store/{yyyyMM}/{UUID}.{확장자}
```

예시:

```text
C:/uploads/store/202607/f5a7....jpg
/uploads/store/202607/f5a7....jpg
```

`WebConfig`는 `/uploads/**` URL을 실제 업로드 폴더와 연결한다.

### 4.8 store 테이블 수정

Service 호출:

```java
int updatedCount = storeMapper.updateStore(store);
```

실제 SQL:

```sql
UPDATE store
   SET name = #{name},
       description = #{description},
       image_url = #{imageUrl},
       address = #{address},
       phone = #{phone},
       pickup_place = #{pickupPlace},
       pickup_start_time = #{pickupStartTime},
       pickup_end_time = #{pickupEndTime},
       pickup_interval_minutes = #{pickupIntervalMinutes}
 WHERE id = #{id}
```

수정된 행이 정확히 하나가 아니면 업무 예외를 발생시킨다.

```java
if (storeMapper.updateStore(store) != 1) {
    throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
}
```

### 4.9 요일별 영업시간 생성

화면은 평일과 주말 영업시간만 입력받지만 DB는 일주일의 각 요일을 한 행씩 저장한다.

Service는 `DayOfWeek.values()`를 순회해 7개 행을 만든다.

```java
for (DayOfWeek day : DayOfWeek.values()) {
    boolean weekend =
        day == DayOfWeek.SATURDAY ||
        day == DayOfWeek.SUNDAY;

    boolean closed = form.getClosedDays().contains(day);

    StoreBusinessHour hour = new StoreBusinessHour();
    hour.setStoreId(DEFAULT_STORE_ID);
    hour.setDayOfWeek(day);
    hour.setClosed(closed);

    hour.setOpenTime(
        closed
            ? null
            : weekend
                ? form.getWeekendOpenTime()
                : form.getWeekdayOpenTime()
    );

    hour.setCloseTime(
        closed
            ? null
            : weekend
                ? form.getWeekendCloseTime()
                : form.getWeekdayCloseTime()
    );

    storeMapper.upsertBusinessHour(hour);
}
```

예를 들어 다음과 같이 입력했다고 가정한다.

```text
평일: 10:00~20:00
주말: 11:00~18:00
정기 휴무: 일요일
```

Service에서 변환되는 데이터:

| 요일 | 시작 | 종료 | 휴무 |
|---|---:|---:|---:|
| 월 | 10:00 | 20:00 | false |
| 화 | 10:00 | 20:00 | false |
| 수 | 10:00 | 20:00 | false |
| 목 | 10:00 | 20:00 | false |
| 금 | 10:00 | 20:00 | false |
| 토 | 11:00 | 18:00 | false |
| 일 | `NULL` | `NULL` | true |

휴무일의 시작·종료 시간을 `NULL`로 만드는 이유는 DB 제약조건과 일치시키기 위해서다.

```sql
CHECK (
    (is_closed = 1
        AND open_time IS NULL
        AND close_time IS NULL)
    OR
    (is_closed = 0
        AND open_time IS NOT NULL
        AND close_time IS NOT NULL
        AND open_time < close_time)
)
```

### 4.10 영업시간 UPSERT

실제 SQL:

```sql
INSERT INTO store_business_hour
    (store_id, day_of_week, open_time, close_time, is_closed)
VALUES
    (#{storeId}, #{dayOfWeek}, #{openTime}, #{closeTime}, #{closed})
ON DUPLICATE KEY UPDATE
    open_time = VALUES(open_time),
    close_time = VALUES(close_time),
    is_closed = VALUES(is_closed)
```

`store_id + day_of_week`가 유일키이기 때문에 다음과 같이 동작한다.

```text
해당 요일 데이터가 없음
  → INSERT

해당 요일 데이터가 있음
  → UPDATE
```

따라서 초기 등록과 이후 수정을 같은 Mapper 메서드로 처리할 수 있다.

### 4.11 트랜잭션 커밋

`updateStore()`가 예외 없이 끝나면 Spring이 트랜잭션을 커밋한다.

```text
store UPDATE 성공
AND
7개 요일 UPSERT 성공
  → 전체 커밋
```

DB 작업 중 예외가 발생하면 매장 기본정보와 영업시간 변경은 함께 롤백된다.

### 4.12 PRG 패턴

Service가 정상 종료되면 Controller는 직접 완료 화면을 반환하지 않고 리다이렉트한다.

```java
redirectAttributes.addFlashAttribute(
    "successMessage",
    "매장 정보를 저장했습니다."
);

return "redirect:/admin/store";
```

전체 흐름:

```text
POST /admin/store
  → 저장 성공
  → HTTP Redirect
  → GET /admin/store
  → 수정된 데이터 재조회
  → 성공 메시지와 함께 화면 출력
```

이 방식을 PRG(Post/Redirect/Get) 패턴이라고 한다.

브라우저 새로고침으로 이전 POST 요청이 다시 전송되는 문제를 방지한다.

---

## 5. 검증 실패 흐름

예를 들어 매장명을 비우고 저장했다고 가정한다.

```text
POST /admin/store
  → StoreUpdateForm에 요청값 바인딩
  → @NotBlank 검증 실패
  → BindingResult.hasErrors() == true
  → StoreService.updateStore()는 호출하지 않음
  → 기존 입력값과 오류 메시지로 form.html 재렌더링
```

Controller 코드:

```java
if (bindingResult.hasErrors()) {
    model.addAttribute("holidayForm", new StoreHolidayForm());
    addReferenceData(model, storeService.getStoreView());
    return "admin/store/form";
}
```

화면에서는 다음 코드로 오류를 출력한다.

```html
<p class="field-error" th:errors="*{name}"></p>
```

검증 실패는 리다이렉트하지 않는다.

사용자가 방금 입력한 값을 그대로 유지하면서 같은 화면을 다시 렌더링하기 위해서다.

---

## 6. 업무 예외 흐름

### 6.1 매장 정보가 없는 경우

```java
private Store findDefaultStore() {
    return storeMapper.findStoreById(DEFAULT_STORE_ID)
        .orElseThrow(
            () -> new BusinessException(StoreErrorCode.NOT_FOUND)
        );
}
```

흐름:

```text
StoreService
  → BusinessException(STORE_001)
  → GlobalExceptionHandler.handleBusiness()
  → HTTP 상태 설정
  → 오류 화면 렌더링
```

### 6.2 이미지 파일이 아닌 경우

```java
throw new BusinessException(StoreErrorCode.INVALID_IMAGE);
```

오류 정보:

```text
HTTP 상태: 400
오류 코드: STORE_005
메시지: 이미지 파일만 첨부할 수 있습니다.
화면: error/4xx
```

### 6.3 매장 UPDATE에 실패한 경우

```java
throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
```

오류 정보:

```text
HTTP 상태: 500
오류 코드: STORE_002
메시지: 매장 정보를 저장하지 못했습니다.
화면: error/500
```

### 6.4 입력 검증과 업무 예외의 차이

| 구분 | 발생 위치 | 처리 결과 |
|---|---|---|
| `@Valid` 입력 오류 | Controller 진입 시 | 같은 입력 폼 재렌더링 |
| 매장 없음 | Service | 전역 404 오류 화면 |
| 잘못된 이미지 | Service | 전역 400 오류 화면 |
| 매장 수정 실패 | Service | 전역 500 오류 화면 |

---

## 7. 최종 호출 흐름 요약

### 7.1 조회

```text
GET /admin/store
  → SecurityConfig
      → ADMIN 권한 확인
  → StoreAdminController.form()
  → StoreService.getStoreView()
  → StoreService.findDefaultStore()
  → StoreMapper.findStoreById(1)
  → StoreMapper.findBusinessHours(1)
  → StoreMapper.findHolidays(1)
  → StoreMapper.xml SELECT
  → StoreView 조합
  → StoreUpdateForm.from(StoreView)
  → Model 생성
  → admin/store/form.html
```

### 7.2 수정 성공

```text
POST /admin/store
  → SecurityConfig
      → ADMIN 권한 확인
      → CSRF 검사
  → StoreAdminController.update()
  → StoreUpdateForm 바인딩
  → @Valid 검증 성공
  → StoreService.updateStore()
      → 트랜잭션 시작
      → StoreMapper.findStoreById(1)
      → Store Entity 값 변경
      → 새 이미지가 있으면 FileStorageClient.store()
      → StoreMapper.updateStore()
      → 이전 이미지가 있으면 FileStorageClient.delete()
      → StoreMapper.upsertBusinessHour() 7회
      → 트랜잭션 커밋
  → successMessage 저장
  → redirect:/admin/store
  → GET /admin/store
```

### 7.3 입력 검증 실패

```text
POST /admin/store
  → StoreAdminController.update()
  → StoreUpdateForm 바인딩
  → @Valid 검증 실패
  → StoreService 호출 안 함
  → admin/store/form.html 재렌더링
```

### 7.4 Service 업무 오류

```text
POST /admin/store
  → StoreAdminController.update()
  → StoreService.updateStore()
  → BusinessException
  → 트랜잭션 롤백
  → GlobalExceptionHandler
  → error/404, error/4xx 또는 error/500
```

---

## 8. 현재 구현에서 확인되는 주의점

### 8.1 파일과 DB 트랜잭션 불일치

DB 작업은 `@Transactional`로 묶여 있지만 파일 저장과 삭제는 DB 트랜잭션에 참여하지 않는다.

현재 처리 순서:

```text
새 이미지 저장
  → store UPDATE
  → 기존 이미지 삭제
  → 영업시간 UPSERT 7회
  → DB 커밋
```

영업시간 저장 중 오류가 발생하면 다음 상태가 생길 수 있다.

```text
DB 변경
  → 롤백됨

새 이미지 파일
  → 디스크에 남음

기존 이미지 파일
  → 이미 삭제됐을 수 있음
```

특히 `StoreService`의 주석은 “DB 저장이 확정된 뒤 기존 파일을 삭제한다”고 설명하지만, 실제 코드는 DB 트랜잭션 커밋 전에 기존 파일을 삭제한다.

개선 방향:

- 트랜잭션 커밋 이후 기존 파일 삭제
- DB 저장 실패 시 새 파일을 삭제하는 보상 로직
- 파일 교체 이벤트를 트랜잭션 완료 이벤트로 처리

### 8.2 이미지 Content-Type 검증

현재 이미지는 다음 조건만 검사한다.

```java
contentType.startsWith("image/")
```

Content-Type 역시 클라이언트가 조작할 수 있으므로 완전한 이미지 검증은 아니다.

개선 방향:

- 실제 파일 시그니처 확인
- 이미지 디코딩 가능 여부 확인
- 허용 확장자 및 MIME 타입 목록 제한

### 8.3 단일 매장 ID 하드코딩

```java
public static final long DEFAULT_STORE_ID = 1L;
```

현재 단일 매장 MVP에는 단순하고 적절하지만 다중 지점을 지원할 수 없는 구조다.

다중 지점으로 확장한다면 로그인한 관리자가 관리할 수 있는 `storeId`를 구하고 권한을 검증하는 과정이 필요하다.

### 8.4 Controller 성공 메시지 상수 불일치

Controller는 다음 문자열을 직접 사용한다.

```java
"successMessage"
```

하지만 `StoreAdminControllerTests`는 현재 존재하지 않는 다음 타입을 import한다.

```java
com.cakeshop.global.web.FlashMessage
```

따라서 현재 테스트는 `compileTestJava` 단계에서 실패한다.

실행한 테스트 명령:

```powershell
.\gradlew.bat test --tests "com.cakeshop.domain.store.*"
```

실패 원인:

```text
package com.cakeshop.global.web does not exist
import com.cakeshop.global.web.FlashMessage;
```

Controller와 테스트 중 한쪽을 기준으로 성공 메시지 키를 통일해야 한다.

---

## 9. 관련 테스트가 검증하려는 내용

### StoreServiceTests

파일:

```text
src/test/java/com/cakeshop/domain/store/service/StoreServiceTests.java
```

주요 검증 내용:

- 매장 기본정보와 7개 요일 영업시간이 함께 수정되는지
- 휴무일의 시작·종료 시간이 `null`로 저장되는지
- 주말에 주말 영업시간이 사용되는지
- 새 이미지를 저장하고 기존 이미지를 삭제하는지
- 이미지가 아닌 업로드를 거부하는지
- 중복된 특정 휴무일을 거부하는지

### StoreAdminControllerTests

파일:

```text
src/test/java/com/cakeshop/domain/store/controller/StoreAdminControllerTests.java
```

주요 검증 내용:

- `GET /admin/store`가 필요한 Model 데이터를 만드는지
- 잘못된 입력에서 Service를 호출하지 않는지
- 정상 수정 후 `/admin/store`로 리다이렉트하는지
- 성공 메시지가 Flash Attribute에 저장되는지

현재는 앞서 설명한 `FlashMessage` import 문제를 해결해야 테스트를 실행할 수 있다.

