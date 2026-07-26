package com.cakeshop.domain.statistics.dto.view;

import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.dto.view.PickupScheduleView;
import com.cakeshop.domain.product.dto.view.LowStockProductView;
import java.util.List;

/**
 * 관리자 대시보드 한 장. 기간 집계가 아니라 <b>오늘 기준 현황</b>이다.
 * 건수 카드(검토 대기·제작 중·취소 요청·미답변 후기)는 현재 상태 스냅샷이다.
 */
public record DashboardView(
    long todayOrderCount,
    long todayNetSalesAmount,
    long underReviewCount,
    long inProductionCount,
    long pendingCancellationCount,
    long unansweredReviewCount,
    List<OrderListView> recentOrders,
    List<PickupScheduleView> todayPickups,
    List<LowStockProductView> lowStockProducts,
    int lowStockThreshold
) {
    public int todayPickupCount() {
        return todayPickups.size();
    }

    public int lowStockCount() {
        return lowStockProducts.size();
    }
}
