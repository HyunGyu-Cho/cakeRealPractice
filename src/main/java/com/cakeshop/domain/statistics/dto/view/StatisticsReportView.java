package com.cakeshop.domain.statistics.dto.view;

import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.order.dto.view.ProductSalesStatsView;
import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import java.math.BigDecimal;
import java.util.List;

/**
 * 통계 화면 한 장. 각 도메인 공개 View를 조합한 결과이며 statistics는 자기 테이블이 없다.
 *
 * <p>{@code orderStats.orderAmount}는 주문 금액, {@code paymentStats.netSalesAmount()}는
 * 순매출이다. 결제 실패·환불 때문에 둘은 일치하지 않으며 화면에서도 나눠 보여준다.
 */
public record StatisticsReportView(
    OrderStatsView orderStats,
    PaymentStatsView paymentStats,
    TrendChartView trend,
    List<ProductSalesStatsView> productSales,
    List<BarItemView> pickupHours,
    long newMemberCount,
    long reviewCount,
    BigDecimal averageRating,
    long usedCouponCount
) {
    private static final int POPULAR_PRODUCT_LIMIT = 5;

    /** 순매출. 화면 요약 카드가 쓴다. */
    public long netSalesAmount() {
        return paymentStats.netSalesAmount();
    }

    /**
     * 인기 상품 TOP 5. 상품별 실적이 이미 수량 내림차순이라 앞부분을 그대로 쓴다
     * (같은 집계를 두 번 돌리지 않는다).
     */
    public List<ProductSalesStatsView> popularProducts() {
        return productSales.size() <= POPULAR_PRODUCT_LIMIT
            ? productSales
            : productSales.subList(0, POPULAR_PRODUCT_LIMIT);
    }
}
