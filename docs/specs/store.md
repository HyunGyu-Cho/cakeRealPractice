---
domain: store
status: approved
approved-at: 2026-07-25
---

# store 스펙

> 기존 표준 수직 슬라이스를 현재 코드와 `docs/store-usecase-flow.md` 기준으로 사후 명문화한 정본이다.
> 범위: 단일 대표 매장 조회·수정, 요일별 영업시간, 특정 휴무일, 대표 이미지, 고객 공개 정보.

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 관리자가 케이크샵의 운영 정보를 관리하고 고객 화면에 동일한 정보를 제공한다.
- 주요 유스케이스:
  1. 관리자 → `/admin/store` 조회 → 대표 매장 정보·영업시간·특정 휴무일 확인
  2. 관리자 → 매장 정보·요일별 영업시간·이미지 수정 → 한 트랜잭션으로 DB 반영
  3. 관리자 → 특정 휴무일 추가·삭제 → 고객 픽업 가능일 계산의 기준 제공
  4. 고객 화면 → 공개 View 조회 → 메인·푸터에 매장 정보 표시

## 2. 상태값

- status 컬럼은 없다.
- `store_business_hour.is_closed`는 상태 enum이 아니라 BOOLEAN이다.
- 특정 휴무일은 `store_holiday` 행의 존재 여부로 표현한다.

## 3. DB

- `store`: 대표 매장 기본 정보와 픽업 운영 시간
- `store_business_hour`: 매장·요일별 7개 행, `(store_id, day_of_week)` UNIQUE
- `store_holiday`: 특정 날짜 휴무, `(store_id, holiday_date)` UNIQUE
- `created_at`·`updated_at`은 DDL 기본값과 `ON UPDATE CURRENT_TIMESTAMP(6)`에 위임한다.

## 4. 도메인 간 인터페이스

- 제공:
  - `StoreService.getPublicStore()` → `StorePublicView`
  - `StoreService.getStoreView()` → 관리자 화면용 `StoreView`
- 사용:
  - `FileStorageClient` → 대표 이미지 저장·삭제
- `home`은 `StorePublicView`만 조합하며 Store Mapper나 Entity에 직접 접근하지 않는다.

## 5. 화면

- `GET /admin/store` → `admin/store/form`
- `POST /admin/store` → 기본 정보·영업시간 수정
- `POST /admin/store/holidays` → 특정 휴무일 추가
- `POST /admin/store/holidays/{holidayId}/delete` → 특정 휴무일 삭제
- 고객 메인과 공통 푸터는 `StorePublicView`를 사용한다.

## 6. 비즈니스 규칙

- MVP는 `store.id=1`인 단일 대표 매장을 사용한다.
- 영업 중인 요일은 `open_time < close_time`, 휴무일은 두 시간이 모두 NULL이어야 한다.
- 화면의 평일·주말 대표 시간은 각각 휴무가 아닌 첫 요일의 시간을 사용한다.
- 특정 휴무일은 같은 날짜를 중복 등록할 수 없다.
- 이미지는 `image/*`만 허용하며 저장 경로는 `/store/{yyyyMM}/{uuid}.{ext}`다.
- 이미지와 DB는 원자적 트랜잭션이 아니므로 DB 실패 시 새 파일을 보상 삭제하고, 기존 파일은 DB 커밋 후 삭제한다.

## 7. 완료 기준

- [x] 매장 기본 정보·7개 요일·특정 휴무일 조회·수정
- [x] 관리자 권한, Form 검증, PRG·FlashMessage
- [x] Service·Controller·파일 저장 테스트
- [x] 이미지 교체의 커밋 후 삭제·실패 보상 처리
- [x] 화면 View에서 Store Entity 직접 노출 제거
