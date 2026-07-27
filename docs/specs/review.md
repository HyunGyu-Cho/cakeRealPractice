---
domain: review
owner: 현규
status: approved
approved-at: 2026-07-26
---

# 후기 스펙

> 범위: 픽업 완료 주문에 대한 후기 작성·수정·삭제, 상품 상세 노출과 평점 집계,
> 관리자 숨김·복구와 답글, 답글 등록 알림(6장 규칙 10). 후기 신고는 범위 밖이다.

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 실제로 픽업까지 마친 고객만 후기를 남겨 상품 평점을 신뢰할 수 있게 만든다.
- 주요 유스케이스:
  1. 고객 → `/reviews`에서 후기 쓸 수 있는 픽업 완료 항목과 내가 쓴 후기를 본다
  2. 고객 → 종합 평점(필수)·세부 3축(선택)·내용·이미지로 후기 작성 → 상품 평점이 즉시 갱신된다
  3. 고객 → 본인 후기 수정·삭제 → 삭제하면 그 주문 항목에 다시 쓸 수 있다
  4. 방문자 → 상품 상세에서 공개 후기와 관리자 답글을 본다
  5. 관리자 → `/admin/reviews`에서 검색·숨김·복구, 후기당 답글 1개 등록·수정

## 2. 상태값

| 컬럼 | 값 | 시작 상태 | 최종 상태 | 전이 |
|---|---|---|---|---|
| `reviews.status` | `VISIBLE / HIDDEN` | `VISIBLE` (DDL DEFAULT) | 없음(양방향) | 관리자만 `VISIBLE ↔ HIDDEN`. 전이는 `ReviewStatus.canTransitionTo()`가 소유 |

인벤토리에 ☐로 열려 있던 `reviews.status`를 여기서 확정했다.

- status가 아닌 것 점검:
  - **삭제** → 상태가 아니라 **행 제거**다(6장 규칙 4). `DELETED` 값을 만들지 않는다.
  - **평균 평점·후기 수** → `products`의 집계 컬럼이지 후기의 상태가 아니다.
  - **작성 가능 여부** → 주문 상태 + 기존 후기 유무의 파생값. 저장하지 않는다.
  - **답글 유무** → `review_replies` 행의 존재 여부다.

## 3. DB

- 사용할 테이블: `reviews`, `review_images`, `review_replies` — 셋 다 V0 ERD에 설계만 있고 생성된 적이 없다.
- 스키마 변경 필요 여부: **필요** — `docs/sql/legacy/V16_review.sql` 신규 (V0_ERD.sql 소급 반영)
  1. 세 테이블을 실제로 만든다(V13이 `order_item_options`·`order_item_images`에 한 것과 같은 상황).
  2. **`taste_rating`·`design_rating`·`service_rating`을 NULL 허용으로 바꾼다.** 고객이 네 축을 모두 채우도록
     강제하면 작성률이 떨어진다. `overall_rating`만 `NOT NULL`이다.
  3. 평점 범위는 DB가 지킨다 — `chk_reviews_*_rating CHECK (... BETWEEN 1 AND 5)` 4건.
  4. `chk_reviews_status`, `uk_reviews_order_item`(주문 항목당 1개), `uk_review_replies_review`(후기당 답글 1개).
  5. 조회 인덱스: `idx_reviews_product_status (product_id, status, id DESC)`,
     `idx_reviews_member (member_id, id DESC)`, `idx_review_images_review (review_id, sort_order)`.
- 시드는 넣지 않는다. 후기는 `PICKED_UP` 주문에 매여 있어 의미 있는 시드를 만들려면 주문까지 조작해야 한다.
- `created_at`/`updated_at`은 DDL DEFAULT에 위임한다.

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드 (`ReviewService`):
  - `PageResult<ProductReviewView> getProductReviews(Long productId, PageRequest)` — 상품 상세의 공개 후기
  - `ReviewSummaryView getProductReviewSummary(Long productId)` — 평균·건수(화면 표시용)
  - `List<BestReviewView> getLatestVisibleReviews(int limit)` — home 메인의 "베스트 후기" 섹션용.
    `VISIBLE`만, 이미지는 첫 장만 썸네일로 내보낸다.
- 내가 사용할 다른 도메인의 공개 Service 메서드 (Mapper 직접 호출 금지):
  - `OrderService.getReviewableItems(Long memberId)` / `findReviewableItem(Long memberId, Long orderItemId)`
    — **신규 공개 계약**(order 도메인에 추가). `PICKED_UP` 주문 항목만 돌려준다.
  - `ProductService.refreshRatingStats(Long productId, long reviewCount, BigDecimal averageRating)`
    — **신규 공개 계약**(product 도메인에 추가). 계산은 review가 하고 **쓰기만** 위임한다.
  - `MemberService.getNicknameMap(Collection<Long>)` — 작성자 닉네임(members JOIN 금지)
- **순환 방지**: 서비스 의존은 `review → order`, `review → product` 단방향이다.
  상품 상세의 후기 목록은 `ProductService`가 아니라 **`ProductController`가 `ReviewService`를 호출**해 조합한다.
  Controller → Service 단방향이라 계층 규칙을 지키면서 서비스 간 순환이 생기지 않는다.

## 5. 화면

- 고객: `GET /reviews`(내 후기함 + 작성 가능 목록, 신규),
  `GET/POST /reviews/new?orderItemId=`(목업 URL 유지), `GET/POST /reviews/{id}/edit`, `POST /reviews/{id}/delete`
- 관리자: `GET /admin/reviews`(목업 전환), `POST /admin/reviews/{id}/status`, `POST /admin/reviews/{id}/reply`
- 상품 상세(`customer/product/detail.html`)의 "예시 데이터" 후기 블록을 공개 후기로 교체한다.
- 목업 JS가 시연하는 임시 동작 중 규칙으로 확정할 것:
  - 목업의 평점 4축은 맛·디자인·**포장**·응대인데 ERD에는 포장 자리가 없다. **"포장"을 "종합"으로 바꾼다.**
  - `data-mock-form data-success-url`은 실제 PRG 리다이렉트로 교체한다.
  - `scripts\import-customer-mockups.ps1`의 `$screenMap`에서 `review-form.html`을 **먼저 제외**한다.
    이로써 맵이 비어 이관할 목업이 없어진다.

## 6. 비즈니스 규칙

- team-plan.md 8장에서 이 도메인과 관련된 항목: "리뷰 작성 가능 조건과 작성 가능 횟수" (미결)
- 확정한 규칙:
  1. **작성 자격** = 본인 주문이면서 `orders.status = 'PICKED_UP'`인 주문 항목. order의 공개 계약으로만 검증한다.
  2. **주문 항목당 후기 1개.** `uk_reviews_order_item`이 최종 방어선이다. 같은 상품을 여러 번 사면 각각 쓸 수 있다 —
     회원·상품당이 아니라 **주문 항목당**이 기준이다.
  3. **종합 평점만 필수**, 세부 3축은 선택이다. 상품 집계는 **종합 평점 기준**으로만 계산한다.
  4. **삭제는 하드 삭제**다. 소프트 삭제로 남기면 `uk_reviews_order_item`이 잡혀 있어 **재작성이 영영 막힌다.**
     삭제 트랜잭션에서 이미지 행·파일과 답글까지 함께 지우고 집계를 재계산한다.
  5. **집계는 증분이 아니라 재계산**이다. 작성·수정·삭제·숨김·복구 어느 경우든 같은 트랜잭션에서
     그 상품의 `VISIBLE` 후기로 `average_rating`·`review_count`를 다시 계산한다.
     증분 가감은 숨김·복구가 섞이면 조용히 어긋난다.
  6. **숨김 후기는 고객 화면과 집계에서 빠진다.** 단 작성자 본인의 후기함에는 "숨김 처리됨"으로 남긴다 —
     흔적 없이 사라지면 문의가 는다.
  7. **답글은 후기당 1개**이며 관리자만 등록·수정한다. 숨김 후기에도 답글은 남는다.
  8. **이미지는 최대 3장, 장당 5MB, JPG/PNG.** 저장은 `FileStorageClient`를 쓰고 경로만 `review_images`에 남긴다.
     수정 시 새 파일을 올리면 기존 이미지를 교체하고 이전 파일은 지운다.
  9. 내용은 **10자 이상**이다(목업 `minlength="10"`을 서버 검증으로 승격).
  10. **알림은 답글 최초 등록에만 발행한다**(2026-07-27 후속 사이클에서 추가. 그전까지는 발행 없음).
      `NotificationType.REVIEW_REPLY`를 더해 13개가 됐고 CHECK는 `docs/sql/legacy/V19_notification_review_reply.sql`이
      맞춘다. 수신자는 후기 작성자, 링크는 `/reviews`다. 답글은 후기당 1개라 **수정 시에는 재발행하지 않는다** —
      같은 답글로 작성자를 반복해서 깨우지 않기 위해서다. 후기 작성·수정·삭제·숨김은 여전히 알림이 없다.

## 7. 완료 기준

- [ ] 정상 흐름·주요 실패 흐름 동작 (작성 → 수정 → 숨김/복구 → 답글 → 삭제 후 재작성)
- [ ] 입력 검증(form DTO)과 접근 권한 적용 (타인 후기 수정·삭제 차단, 비 PICKED_UP 작성 차단)
- [ ] 집계 재계산이 작성·수정·삭제·숨김·복구 모두에서 한 트랜잭션으로 처리
- [ ] 전용 테스트 통과 (Service + Controller + 화면 렌더링)
- [ ] enum ↔ DDL CHECK 동기화 테스트
- [ ] 관련 SQL·문서 함께 수정 (V16 + V0 소급, conventions 인벤토리, team-plan 8장, README, TodoList)
