---
domain: statistics
status: approved
approved-at: 2026-07-26
---

# statistics 스펙

관리자 대시보드(`/admin`)와 통계(`/admin/statistics`) 두 화면을 실구현으로 전환한다.
TodoList 5단계 항목이며, 전 도메인이 실구현된 상태를 전제로 한다.

## 1. 개요와 유스케이스

- 해결하는 문제: 관리자가 **오늘 처리할 일**과 **기간 실적**을 한 화면에서 파악한다.
- 유스케이스
  1. 관리자 → `/admin` 진입 → 오늘 기준 실시간 현황(주문·매출·대기 건수, 오늘 픽업 일정, 재고 부족, 처리할 작업)을 본다.
  2. 관리자 → `/admin/statistics`에서 기간(시작일~종료일)과 집계 단위(일/주/월)를 지정해 조회 → 요약 지표·추이·상품별 실적·인기 상품·시간대별 픽업·기타 지표를 본다.
  3. 관리자 → 대시보드의 각 항목에서 해당 관리 화면(`/admin/orders`, `/admin/products` 등)으로 이동한다.

## 2. 상태값

**신규 상태값 없음.** statistics는 조회 전용 도메인이라 소유하는 테이블도 status 컬럼도 없다.
집계 단위 `StatsPeriod`(`DAY`/`WEEK`/`MONTH`)는 **DB에 저장하지 않는 조회 파라미터 enum**이며 status가 아니다.

- status 아님 점검: 재고 부족·품절은 `products.stock_quantity`의 파생값, 모든 지표는 집계 파생값이라 저장하지 않는다.

## 3. DB

- **소유 테이블 없음. 신규 V파일 없음(스키마 변경 0).**
- 집계 원본은 각 도메인이 자기 테이블에서 읽는다: `orders`·`order_items`(order), `payments`·`payment_cancellations`(payment), `members`(member), `products`(product), `reviews`(review), `member_coupons`(coupon).
- **`statistics` 전용 Mapper는 만들지 않고 기존 `StatisticsMapper`/XML을 삭제한다.** 절대규칙(다른 도메인 테이블 직접 JOIN·타 도메인 Mapper 호출 금지) 때문에 statistics는 `home`과 같은 **얇은 조합 계층**이 된다.
- 집계 쿼리는 **각 도메인의 자기 Mapper XML에 추가**하고 해당 도메인 Service가 공개 View로 노출한다. 집계 결과 캐시 테이블은 두지 않는다(실시간 조회).

## 4. 도메인 간 인터페이스

statistics가 제공하는 공개 메서드는 없다(최종 소비자). 아래는 **각 도메인에 새로 추가할 공개 집계 계약**이다.

| 도메인 | 추가할 공개 메서드 (Service) | 반환 |
|---|---|---|
| order | `OrderStatsService.getOrderStats(from, to)` | `OrderStatsView(주문·완료·취소·검토대기·제작중 건수, 주문금액 합계)` |
| order | `OrderStatsService.getOrderTrend(from, to, period)` | `List<OrderTrendPointView(라벨, 주문건수, 주문금액)>` |
| order | `OrderStatsService.getProductSalesStats(from, to, limit)` | `List<ProductSalesStatsView(productId, 상품명, 주문건수, 수량, 주문금액)>` |
| order | `OrderStatsService.getPickupHourCounts(from, to)` | `List<PickupHourCountView(hour, 건수)>` |
| order | `OrderStatsService.getPickupSchedule(date)` | `List<PickupScheduleView(pickupAt, 주문번호, 대표상품, status)>` |
| order | `OrderStatsService.getRecentOrders(limit)` | `List<OrderListView>` (기존 관리자 목록 View 재사용) |
| order | `OrderStatsService.countByStatus(status)` | `long` (승인 대기·제작 중) |
| payment | `PaymentStatsService.getPaymentStats(from, to)` | `PaymentStatsView(결제 건수, 결제 금액, 환불 금액)` — 순매출 = 결제 − 환불 |
| payment | `PaymentStatsService.getSalesTrend(from, to, period)` | `List<SalesTrendPointView(라벨, 순매출)>` |
| payment | `PaymentStatsService.countPendingCancellations()` | `long` (취소·환불 요청) |
| member | `MemberService.countNewMembers(from, to)` | `long` |
| product | `ProductService.getLowStockProducts(threshold)` | `List<LowStockProductView(productId, 상품명, 재고)>` |
| review | `ReviewService.getReviewStats(from, to)` | `ReviewStatsView(작성 수, 평균 평점)` |
| review | `ReviewService.countUnansweredVisibleReviews()` | `long` (대시보드 "처리할 작업" — 미답변은 파생값) |
| coupon | `CouponService.countUsedCoupons(from, to)` | `long` |

- **매출은 payment 도메인만 계산한다.** order는 `orders.final_amount` 기준 "주문 금액"까지만 노출한다
  (order가 `payments`를 JOIN하면 도메인 경계 위반). 추이 그래프는 statistics가 두 계열을 **라벨로 합친다**.
- 모든 메서드는 `@Transactional(readOnly = true)`이고 기간은 `LocalDate from`, `LocalDate to`(경계 포함)를 받아
  도메인 내부에서 `[from 00:00, to+1일 00:00)` 반열린 구간으로 변환한다.
- 집계 단위 enum `StatsPeriod`는 여러 도메인이 함께 쓰므로 `global/common/stats/StatsPeriod.java`에 둔다
  (⚠️ `global/*` 추가 — PR에 "합의 필요"로 명시). SQL 라벨 포맷은 `${}` 금지 규칙 때문에
  Mapper XML의 `<choose>` 분기에 리터럴로 둔다.
- `StatisticsService`는 위 서비스들을 주입받아 화면용 View(`DashboardView`, `StatisticsReportView`)로 조합만 한다.

## 5. 화면

| URL | 템플릿 | 비고 |
|---|---|---|
| `GET /admin` | `admin/dashboard.html` | 오늘 기준 실시간 현황. mock-notice 제거 |
| `GET /admin/statistics` | `admin/statistics.html` | `startDate`·`endDate`·`period` 쿼리 파라미터. mock-notice 제거 |

- URL·템플릿·마크업 구조는 유지하고 하드코딩 숫자만 `th:text`/`th:each`로 교체한다.
- 목업 JS 임시 동작 확정: 일/주/월 버튼은 `data-select-group` 클라이언트 토글이었으나 **서버 조회 파라미터 `period`로 확정**한다(GET 폼 submit, 선택값은 `th:classappend`로 표시).
- 그래프: 외부 차트 라이브러리(CDN)를 쓰지 않는다. **서버 데이터로 그리는 인라인 SVG**로 교체한다 —
  일별 추이는 꺾은선(주문 건수·매출 2계열), 시간대별 픽업은 막대. SVG는 Thymeleaf가 Model 데이터로 직접 그리고
  좌표 계산은 View record(정규화된 0~100 좌표)가 미리 끝내 템플릿에 계산식을 두지 않는다. 스타일은 `app.css`에만 추가한다.
- 기간 기본값: 종료일 = 오늘, 시작일 = 오늘 −29일, `period=DAY`.
- 입력 검증(`StatisticsSearchForm`): 시작일 ≤ 종료일(`@AssertTrue`), 최대 조회 범위 366일. 위반 시 리다이렉트 없이 화면 재렌더 + `errorMessage`.
- 접근 권한: 두 화면 모두 `ADMIN` (기존 `SecurityConfig`의 `/admin/**` 규칙으로 이미 보호됨 — 변경 없음).

## 6. 비즈니스 규칙 확정

team-plan 8장에는 statistics 미결 항목이 없고, 133~136행의 "후반 별도 착수"만 있다. 아래를 이 스펙에서 확정한다.

1. **매출의 정의** = `payments.status = 'DONE'`인 결제 금액 합계에서 `payment_cancellations.status = 'DONE'`의 취소 금액을 뺀 **순매출**. 기준 시각은 `payments.approved_at`(취소는 `canceled_at`). 주문 테이블의 `final_amount`는 "주문 금액"으로 따로 표기해 결제 실패분과 구분한다.
2. **주문 건수 기준** = `orders.created_at`. 결제 전 주문은 생성되지 않으므로 생성 시각이 곧 유효 주문 시각이다.
3. **완료/취소 건수** = 기간 내 `picked_up_at` / `canceled_at`이 든 주문(상태 스냅샷이 아니라 기간 내 발생 기준). `REJECTED`는 취소와 분리해 표기하지 않고 요약에서 제외한다.
4. **상품별·인기 상품 집계** = `order_items` 기준이며 취소·반려 주문(`CANCELED`/`REJECTED`)은 제외한다. 인기 상품 TOP 5는 주문 수량(`SUM(quantity)`) 기준.
5. **재고 부족 임계값 = 5개 이하**(`stock_quantity IS NOT NULL AND stock_quantity <= 5`). 상수로 두고 화면에 표기한다.
6. **신규 회원** = 기간 내 `members.created_at`, 탈퇴자(`WITHDRAWN`)도 가입 시점 실적이므로 포함한다.
7. **평균 평점** = 기간 내 작성된 `VISIBLE` 후기의 `overall_rating` 평균(숨김 제외), 소수 1자리.
8. **주별 라벨**은 ISO 주(월요일 시작) `YYYY-Www`, 월별은 `YYYY-MM`, 일별은 `MM-DD`.

## 7. 완료 기준

- [ ] `/admin`·`/admin/statistics`가 실데이터로 렌더되고 mock-notice가 사라진다
- [ ] 기간·집계 단위 조회가 동작하고 잘못된 기간은 재렌더로 막힌다
- [ ] statistics는 자체 Mapper 없이 타 도메인 공개 Service만 호출한다(계층 규칙 준수)
- [ ] 각 도메인에 추가한 집계 메서드의 Service 테스트 + `StatisticsAdminControllerTests` 통과
- [ ] 목업 스모크 테스트에서 두 화면 제외, README 현황표·TodoList 갱신
- [ ] 스키마 변경 없음(신규 V파일 0)을 PR에 명시
