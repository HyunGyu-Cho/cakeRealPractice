package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.order.dto.view.OrderTrendPointView;
import com.cakeshop.domain.order.dto.view.PickupHourCountView;
import com.cakeshop.domain.order.service.OrderStatsService;
import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import com.cakeshop.domain.payment.dto.view.SalesTrendPointView;
import com.cakeshop.domain.payment.service.PaymentStatsService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.view.ReviewStatsView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.BarItemView;
import com.cakeshop.domain.statistics.dto.view.DashboardView;
import com.cakeshop.domain.statistics.dto.view.StatisticsReportView;
import com.cakeshop.domain.statistics.dto.view.TrendChartView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticsServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);

    private OrderStatsService orderStatsService;
    private PaymentStatsService paymentStatsService;
    private MemberService memberService;
    private ProductService productService;
    private ReviewService reviewService;
    private CouponService couponService;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        orderStatsService = mock(OrderStatsService.class);
        paymentStatsService = mock(PaymentStatsService.class);
        memberService = mock(MemberService.class);
        productService = mock(ProductService.class);
        reviewService = mock(ReviewService.class);
        couponService = mock(CouponService.class);
        statisticsService = new StatisticsService(
            orderStatsService, paymentStatsService, memberService, productService,
            reviewService, couponService,
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));

        when(orderStatsService.getOrderStats(any(), any())).thenReturn(OrderStatsView.empty());
        when(paymentStatsService.getPaymentStats(any(), any())).thenReturn(PaymentStatsView.empty());
        when(orderStatsService.getOrderTrend(any(), any(), any())).thenReturn(List.of());
        when(paymentStatsService.getSalesTrend(any(), any(), any())).thenReturn(List.of());
        when(orderStatsService.getProductSalesStats(any(), any(), anyInt())).thenReturn(List.of());
        when(orderStatsService.getPickupHourCounts(any(), any())).thenReturn(List.of());
        when(orderStatsService.getPickupSchedule(any())).thenReturn(List.of());
        when(orderStatsService.getRecentOrders(anyInt())).thenReturn(List.of());
        when(productService.getLowStockProducts(anyInt())).thenReturn(List.of());
        when(reviewService.getReviewStats(any(), any())).thenReturn(ReviewStatsView.empty());
    }

    @Test
    void 대시보드는_오늘_하루만_집계한다() {
        DashboardView dashboard = statisticsService.getDashboard();

        assertThat(dashboard.lowStockThreshold()).isEqualTo(StatisticsService.LOW_STOCK_THRESHOLD);
        verify(orderStatsService).getOrderStats(TODAY, TODAY);
        verify(paymentStatsService).getPaymentStats(TODAY, TODAY);
        verify(orderStatsService).getPickupSchedule(TODAY);
    }

    @Test
    void 대시보드_건수는_상태_스냅샷과_대기_건수를_그대로_담는다() {
        when(orderStatsService.countByStatus("UNDER_REVIEW")).thenReturn(3L);
        when(orderStatsService.countByStatus("IN_PRODUCTION")).thenReturn(5L);
        when(paymentStatsService.countPendingCancellations()).thenReturn(1L);
        when(reviewService.countUnansweredVisibleReviews()).thenReturn(2L);

        DashboardView dashboard = statisticsService.getDashboard();

        assertThat(dashboard.underReviewCount()).isEqualTo(3);
        assertThat(dashboard.inProductionCount()).isEqualTo(5);
        assertThat(dashboard.pendingCancellationCount()).isEqualTo(1);
        assertThat(dashboard.unansweredReviewCount()).isEqualTo(2);
    }

    @Test
    void 추이는_주문과_순매출을_라벨로_합치고_한쪽만_있는_라벨도_남긴다() {
        when(orderStatsService.getOrderTrend(any(), any(), any())).thenReturn(List.of(
            new OrderTrendPointView("07-18", 2, 20000),
            new OrderTrendPointView("07-19", 4, 40000)));
        when(paymentStatsService.getSalesTrend(any(), any(), any())).thenReturn(List.of(
            new SalesTrendPointView("07-19", 35000),
            new SalesTrendPointView("07-20", 10000)));

        TrendChartView trend = statisticsService.getReport(form()).trend();

        assertThat(trend.points()).extracting("label")
            .containsExactly("07-18", "07-19", "07-20");
        assertThat(trend.points()).extracting("orderCount").containsExactly(2L, 4L, 0L);
        assertThat(trend.points()).extracting("netSalesAmount")
            .containsExactly(0L, 35000L, 10000L);
        assertThat(trend.maxOrderCount()).isEqualTo(4);
        assertThat(trend.maxNetSalesAmount()).isEqualTo(35000);
    }

    @Test
    void 추이_좌표는_0에서_100으로_정규화된다() {
        when(orderStatsService.getOrderTrend(any(), any(), any())).thenReturn(List.of(
            new OrderTrendPointView("07-19", 0, 0),
            new OrderTrendPointView("07-20", 10, 100000)));

        TrendChartView trend = statisticsService.getReport(form()).trend();

        assertThat(trend.points().getFirst().x()).isZero();
        assertThat(trend.points().getLast().x()).isEqualTo(100.0);
        // 값이 클수록 y는 작아진다(SVG는 위가 0이다).
        assertThat(trend.points().getFirst().orderY()).isEqualTo(100.0);
        assertThat(trend.points().getLast().orderY()).isZero();
        assertThat(trend.orderPolyline()).isEqualTo("0.0,100.0 100.0,0.0");
    }

    @Test
    void 데이터가_없으면_빈_차트를_돌려준다() {
        TrendChartView trend = statisticsService.getReport(form()).trend();

        assertThat(trend.isEmpty()).isTrue();
        assertThat(trend.orderPolyline()).isEmpty();
    }

    @Test
    void 시간대별_막대는_픽업이_없는_시간도_24칸으로_채운다() {
        when(orderStatsService.getPickupHourCounts(any(), any())).thenReturn(List.of(
            new PickupHourCountView(11, 2), new PickupHourCountView(14, 4)));

        List<BarItemView> bars = statisticsService.getReport(form()).pickupHours();

        assertThat(bars).hasSize(24);
        assertThat(bars.get(0).value()).isZero();
        assertThat(bars.get(11).value()).isEqualTo(2);
        assertThat(bars.get(11).heightPercent()).isEqualTo(50.0);
        assertThat(bars.get(14).heightPercent()).isEqualTo(100.0);
    }

    @Test
    void 기타_지표는_각_도메인의_공개_계약_결과를_그대로_담는다() {
        when(memberService.countNewMembers(any(), any())).thenReturn(23L);
        when(couponService.countUsedCoupons(any(), any())).thenReturn(47L);
        when(reviewService.getReviewStats(any(), any()))
            .thenReturn(new ReviewStatsView(9, new BigDecimal("4.3")));

        StatisticsReportView report = statisticsService.getReport(form());

        assertThat(report.newMemberCount()).isEqualTo(23);
        assertThat(report.usedCouponCount()).isEqualTo(47);
        assertThat(report.reviewCount()).isEqualTo(9);
        assertThat(report.averageRating()).isEqualByComparingTo("4.3");
    }

    @Test
    void 순매출은_결제에서_환불을_뺀_값이다() {
        when(paymentStatsService.getPaymentStats(any(), any()))
            .thenReturn(new PaymentStatsView(12, 4_820_000, 320_000));

        assertThat(statisticsService.getReport(form()).netSalesAmount()).isEqualTo(4_500_000);
    }

    @Test
    void 조회_기간은_form이_준_구간을_그대로_각_도메인에_넘긴다() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setStartDate(LocalDate.of(2026, 7, 1));
        form.setEndDate(LocalDate.of(2026, 7, 15));

        statisticsService.getReport(form);

        verify(memberService)
            .countNewMembers(eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 15)));
    }

    private StatisticsSearchForm form() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.applyDefaults(TODAY);
        return form;
    }
}
