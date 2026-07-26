---
domain: product
status: approved
approved-at: 2026-07-25
---

# product 스펙

> 확정일: 2026-07-25. 범위: 고객 상품 목록(필터·정렬·검색·페이징 — 목업 전체)·상세, 관리자 CRUD + 대표 이미지 1장, 공개 계약 getSalesInfo.
> 제외: 상품 옵션(product_option_groups·product_options) 관리 — order(수제) 차례에 다룬다. 이미지 갤러리(여러 장)는 추후 확장.
> 이 도메인이 팀의 "목록·페이징 포함 전체 CRUD 표준 예시"다.

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 판매 상품의 등록·관리와 고객 노출(목록·상세), 타 도메인(cart·order·home)에 판매 정보 제공.
- 주요 유스케이스:
  1. 고객 → 목록에서 유형·가격대·판매상태·픽업가능 필터 + 정렬(최신/가격↑↓/인기) + 상품명 검색 + 페이지 이동 → 조건에 맞는 상품 카드
  2. 고객 → 상세 조회 → 이미지·가격·판매/재고 상태·픽업 가능일 표시 → 일반 판매 상품은 DB 장바구니에 담기
  3. 관리자 → 목록(검색·필터·페이징) → 등록/수정(대표 이미지 업로드 포함) → 판매 중지/재개
  4. 타 도메인 → `getSalesInfo(productId)` → 판매 가능 여부·가격·재고

## 2. 상태값 (conventions.md 상태값 공통 규칙 준수)

| 컬럼 | 값(영문 enum 이름) | 시작 상태 | 최종 상태 | 전이 규칙 요약 |
|---|---|---|---|---|
| `products.status` | `ACTIVE / INACTIVE` | `ACTIVE` (DEFAULT) | 없음 | `ACTIVE ↔ INACTIVE` (관리자 판매 스위치, 자유 전환) |

- **status가 아닌 것**: 품절·재고 부족은 `stock_quantity` 파생값(품절 = 0, 재고 부족 = 1~4). 절대 status에 합치지 않는다.
- `product_type`은 상태가 아니라 종류: `GENERAL / CUSTOM / SAME_DAY / SEASON` (저장 규칙은 status와 동일 — 영문 enum 이름).
- `product_options.status`는 이번 범위 밖이었고 **order(수제) 차례에 `ACTIVE / INACTIVE`로 확정됐다**
  (V13, 스펙 `docs/specs/order-custom.md`). 옵션 그룹·옵션 조회 공개 계약 `ProductService.getOptionGroups`도 거기서 정의한다.

## 3. DB (V6_products_catalog.sql + V0/V1 소급)

- `products.status`: `VARCHAR(20)` + `chk_products_status CHECK (ACTIVE/INACTIVE)` 표준화.
- `products.stock_quantity INT UNSIGNED NULL` 추가 — **NULL = 재고 관리 안 함**(주문제작 상품), 숫자 = 재고 수량.
- `product_images` 테이블 추가(V0 정의 재사용) — 1차에서는 상품당 대표 1행만 사용.
- `categories` 시드 4행: `GENERAL(일반 케이크) / CUSTOM(주문 제작) / SAME_DAY(당일 픽업) / SEASON(시즌 상품)`.
  1차에서는 category와 product_type이 1:1(코드 동일)이며, 관리자 폼의 유형 선택이 둘 다 결정한다(시즌 세분화 등 확장 대비 컬럼은 분리 유지).

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드:
  - `ProductService.getSalesInfo(Long productId)` → `ProductSalesInfo(onSale, price, stockQuantity)` — 1차 합의 계약(`ProductQueryService.getSalesInfo`)의 이행. cart가 사용 중이며 order가 사용 예정.
  - `ProductService.getLatestActiveProducts(int limit)` → home 메인 노출용.
- 내가 사용할 다른 도메인의 공개 Service: 없음.

## 5. 화면

- URL·템플릿 유지: `GET /products`(목록), `GET /products/{id}`(상세), `GET/POST /admin/products`(목록/등록), `/admin/products/new`, `/admin/products/{id}/edit`.
- 고객 목록 필터(목업 전체): `type`(4종), `minPrice`/`maxPrice`, `sale`(전체/판매중/품절/판매중지), `pickupToday`(픽업 가능 = `preparation_days = 0`), `keyword`(상품명), 정렬 `sort`(`latest`(기본)/`priceAsc`/`priceDesc`/`popular`(리뷰 수↓)). 페이지 번호 페이징(공용 pagination 프래그먼트).
- 관리자 목록 필터: `keyword`/`type`/`status`/`stock`(전체/재고있음/품절) + 페이징.
- 상세: 대표 이미지, 판매 상태·재고 상태(파생 라벨), 알레르기·후기 영역은 "예시 데이터" 배지로 목업 유지(review 차례에 교체). 일반 상품 장바구니 버튼은 서버 폼이며 주문제작 동선은 order(수제)에서 구현한다.
- 고객 목록·상세 템플릿은 import 스크립트 덮어쓰기 목록에서 제외한다.
- home 메인의 상품 목업 영역을 `getLatestActiveProducts`로 교체(각 도메인 완성 시 home 연결 규칙).

## 6. 비즈니스 규칙 확정

- 가격: `base_price DECIMAL(12,0)`, 0 이상. 수정 시 즉시 반영(주문 스냅샷은 order 도메인이 소유).
- 재고: 주문제작(CUSTOM) 상품은 재고 없음(NULL). 그 외 유형은 재고 숫자 입력. 재고 차감·복구 시점은 order 차례에 확정(team-plan 8장).
- 판매 가능(`onSale`) = `status = ACTIVE` AND (재고 관리 안 함 OR `stock_quantity > 0`).
- 삭제: 하드 삭제 금지(주문 스냅샷·FK 보존) — 판매 중지(INACTIVE)로 대신한다. 관리자 목록의 "삭제" 버튼은 제거하고 중지/재개 토글만 둔다.
- 고객 목록은 INACTIVE 상품도 "판매 중지" 배지로 노출(목업과 동일). 상세도 조회 가능하되 구매 동선 버튼 비활성.
- 대표 이미지: `image/*`만 허용(store와 동일 검증), 교체 시 DB 저장 성공 후 이전 파일 삭제.

## 7. 완료 기준 (2026-07-25 전 항목 충족 — E2E 12개 항목 검증 완료)

- [x] 관리자 등록 → 고객 목록/상세 노출 → 수정·중지 반영 전 흐름 동작
- [x] 필터·정렬·검색·페이징 조합 동작(고객·관리자)
- [x] 입력 검증(form DTO)과 접근 권한(/admin/** = ADMIN) 적용
- [x] 전용 테스트 통과 (Service + Controller)
- [x] 관련 SQL(V6 + V0/V1)·문서(conventions·README·TodoList) 함께 수정
