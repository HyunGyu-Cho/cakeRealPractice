package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.order.dto.view.OrderTrendPointView;
import com.cakeshop.domain.order.dto.view.PickupHourCountView;
import com.cakeshop.domain.order.dto.view.PickupScheduleView;
import com.cakeshop.domain.order.dto.view.ProductSalesStatsView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.service.OrderStatsService;
import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import com.cakeshop.domain.payment.dto.view.SalesTrendPointView;
import com.cakeshop.domain.payment.service.PaymentStatsService;
import com.cakeshop.domain.product.dto.view.LowStockProductView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.view.ReviewStatsView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.BarItemView;
import com.cakeshop.domain.statistics.dto.view.DashboardView;
import com.cakeshop.domain.statistics.dto.view.StatisticsReportView;
import com.cakeshop.domain.statistics.dto.view.TrendChartView;
import com.cakeshop.domain.statistics.dto.view.TrendPointView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전 도메인의 공개 집계 계약을 화면 한 장으로 조합하는 얇은 계층. home과 같은 성격이라
 * 전용 Mapper도 테이블도 없다 — 집계 SQL은 각 도메인이 자기 테이블에서 돌린다.
 */
@Service
public class StatisticsService {
    /** 재고 부족 임계값. "몇 개부터 부족인지"는 통계 화면의 판단이라 여기서 소유한다. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private static final int RECENT_ORDER_LIMIT = 5;
    private static final int PRODUCT_SALES_LIMIT = 10;

    /** 추이 차트 좌표계: 구간 하나당 폭과 그 결과를 묶는 범위(가로:세로 3:1 ~ 9:1). */
    private static final int TREND_STEP = 12;
    private static final int TREND_MIN_WIDTH = 300;
    private static final int TREND_MAX_WIDTH = 900;

    private final OrderStatsService orderStatsService;
    private final PaymentStatsService paymentStatsService;
    private final MemberService memberService;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final CouponService couponService;
    private final Clock clock;

    @Autowired
    public StatisticsService(OrderStatsService orderStatsService,
                             PaymentStatsService paymentStatsService,
                             MemberService memberService,
                             ProductService productService,
                             ReviewService reviewService,
                             CouponService couponService) {
        this(orderStatsService, paymentStatsService, memberService, productService,
            reviewService, couponService, Clock.systemDefaultZone());
    }

    /** 테스트가 "오늘"을 고정하려고 쓰는 생성자. coupon과 같은 방식이다. */
    public StatisticsService(OrderStatsService orderStatsService,
                             PaymentStatsService paymentStatsService,
                             MemberService memberService,
                             ProductService productService,
                             ReviewService reviewService,
                             CouponService couponService,
                             Clock clock) {
        this.orderStatsService = orderStatsService;
        this.paymentStatsService = paymentStatsService;
        this.memberService = memberService;
        this.productService = productService;
        this.reviewService = reviewService;
        this.couponService = couponService;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 대시보드는 오늘 하루만 본다. */
    @Transactional(readOnly = true)
    public DashboardView getDashboard() {
        LocalDate today = today();
        OrderStatsView orderStats = orderStatsService.getOrderStats(today, today);
        PaymentStatsView paymentStats = paymentStatsService.getPaymentStats(today, today);
        List<PickupScheduleView> pickups = orderStatsService.getPickupSchedule(today);
        List<LowStockProductView> lowStock =
            productService.getLowStockProducts(LOW_STOCK_THRESHOLD);
        return new DashboardView(
            orderStats.orderCount(),
            paymentStats.netSalesAmount(),
            orderStatsService.countByStatus(OrderStatus.UNDER_REVIEW.name()),
            orderStatsService.countByStatus(OrderStatus.IN_PRODUCTION.name()),
            paymentStatsService.countPendingCancellations(),
            reviewService.countUnansweredVisibleReviews(),
            orderStatsService.getRecentOrders(RECENT_ORDER_LIMIT),
            pickups,
            lowStock,
            LOW_STOCK_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public StatisticsReportView getReport(StatisticsSearchForm form) {
        LocalDate from = form.getStartDate();
        LocalDate to = form.getEndDate();
        ReviewStatsView reviewStats = reviewService.getReviewStats(from, to);
        return new StatisticsReportView(
            orderStatsService.getOrderStats(from, to),
            paymentStatsService.getPaymentStats(from, to),
            buildTrend(
                orderStatsService.getOrderTrend(from, to, form.resolvedPeriod()),
                paymentStatsService.getSalesTrend(from, to, form.resolvedPeriod())),
            orderStatsService.getProductSalesStats(from, to, PRODUCT_SALES_LIMIT),
            toPickupBars(orderStatsService.getPickupHourCounts(from, to)),
            memberService.countNewMembers(from, to),
            reviewStats.reviewCount(),
            reviewStats.averageRating(),
            couponService.countUsedCoupons(from, to));
    }

    /**
     * 주문 추이(order)와 순매출 추이(payment)를 라벨로 합쳐 SVG 좌표까지 계산한다.
     * 두 도메인 중 한쪽에만 있는 라벨도 버리지 않고 다른 계열을 0으로 채운다.
     */
    private TrendChartView buildTrend(List<OrderTrendPointView> orderTrend,
                                      List<SalesTrendPointView> salesTrend) {
        Map<String, long[]> merged = new LinkedHashMap<>();
        for (OrderTrendPointView point : orderTrend) {
            merged.computeIfAbsent(point.label(), key -> new long[3]);
            long[] values = merged.get(point.label());
            values[0] = point.orderCount();
            values[1] = point.orderAmount();
        }
        for (SalesTrendPointView point : salesTrend) {
            merged.computeIfAbsent(point.label(), key -> new long[3])[2] =
                point.netSalesAmount();
        }
        if (merged.isEmpty()) {
            return TrendChartView.empty();
        }
        List<String> labels = merged.keySet().stream().sorted().toList();
        long maxOrderCount = merged.values().stream().mapToLong(v -> v[0]).max().orElse(0);
        long maxSales = merged.values().stream().mapToLong(v -> Math.max(v[2], 0)).max().orElse(0);
        int width = viewBoxWidth(labels.size());

        List<TrendPointView> points = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            long[] values = merged.get(labels.get(i));
            double x = labels.size() == 1 ? width / 2.0 : i * (double) width / (labels.size() - 1);
            points.add(new TrendPointView(
                labels.get(i), values[0], values[1], values[2],
                round(x), round(toY(values[0], maxOrderCount)), round(toY(values[2], maxSales))));
        }
        return new TrendChartView(
            points,
            points.stream().map(p -> p.x() + "," + p.orderY()).collect(Collectors.joining(" ")),
            points.stream().map(p -> p.x() + "," + p.salesY()).collect(Collectors.joining(" ")),
            maxOrderCount, maxSales, width);
    }

    /**
     * 구간 수에 맞춘 좌표계 폭. 높이가 100이라 이 값이 곧 가로:세로 비율이므로,
     * 너무 좁으면(정사각형에 가까우면) 차트가 세로로 길어지고 너무 넓으면 납작해진다.
     * 그래서 구간당 {@link #TREND_STEP}을 주되 {@link #TREND_MIN_WIDTH}~{@link #TREND_MAX_WIDTH}로 묶는다.
     */
    private int viewBoxWidth(int labelCount) {
        int spanned = Math.max(labelCount - 1, 0) * TREND_STEP;
        return Math.clamp(spanned, TREND_MIN_WIDTH, TREND_MAX_WIDTH);
    }

    /** 24시간 전부를 자리로 남긴다 — 픽업이 없는 시간대도 막대 축에 있어야 분포가 읽힌다. */
    private List<BarItemView> toPickupBars(List<PickupHourCountView> hourCounts) {
        Map<Integer, Long> byHour = hourCounts.stream()
            .collect(Collectors.toMap(PickupHourCountView::hour,
                PickupHourCountView::pickupCount));
        long max = byHour.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<BarItemView> bars = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            long value = byHour.getOrDefault(hour, 0L);
            bars.add(new BarItemView(
                String.format("%02d", hour), value,
                round(max == 0 ? 0 : value * 100.0 / max)));
        }
        return bars;
    }

    /** SVG는 위가 y=0이므로 값이 클수록 y가 작아진다. 최댓값이 0이면 바닥선을 그린다. */
    private double toY(long value, long max) {
        return max <= 0 ? 100.0 : 100.0 - (value * 100.0 / max);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
