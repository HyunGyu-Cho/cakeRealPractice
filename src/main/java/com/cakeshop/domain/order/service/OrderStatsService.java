package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.order.dto.view.OrderTrendPointView;
import com.cakeshop.domain.order.dto.view.PickupHourCountView;
import com.cakeshop.domain.order.dto.view.PickupScheduleView;
import com.cakeshop.domain.order.dto.view.ProductSalesStatsView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.common.stats.StatsPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * order 도메인이 statistics에 공개하는 집계 계약. 전부 조회 전용이다.
 *
 * <p>금액은 주문 금액(final_amount)까지만 노출한다 — 순매출은 payments를 가진 payment 도메인이
 * 계산해야 하므로 여기서 결제 테이블을 JOIN하지 않는다.
 */
@Service
public class OrderStatsService {
    private final OrderMapper orderMapper;
    private final OrderAdminService orderAdminService;

    public OrderStatsService(OrderMapper orderMapper, OrderAdminService orderAdminService) {
        this.orderMapper = orderMapper;
        this.orderAdminService = orderAdminService;
    }

    @Transactional(readOnly = true)
    public OrderStatsView getOrderStats(LocalDate from, LocalDate to) {
        OrderStatsView stats = orderMapper.aggregateOrderStats(startOf(from), endOf(to));
        return stats == null ? OrderStatsView.empty() : stats;
    }

    @Transactional(readOnly = true)
    public List<OrderTrendPointView> getOrderTrend(
        LocalDate from, LocalDate to, StatsPeriod period) {
        return orderMapper.aggregateOrderTrend(startOf(from), endOf(to), period);
    }

    @Transactional(readOnly = true)
    public List<ProductSalesStatsView> getProductSalesStats(
        LocalDate from, LocalDate to, int limit) {
        return orderMapper.aggregateProductSales(startOf(from), endOf(to), limit);
    }

    @Transactional(readOnly = true)
    public List<PickupHourCountView> getPickupHourCounts(LocalDate from, LocalDate to) {
        return orderMapper.aggregatePickupHours(startOf(from), endOf(to));
    }

    /** 해당 날짜의 픽업 일정. 취소·반려 주문은 빠진다. */
    @Transactional(readOnly = true)
    public List<PickupScheduleView> getPickupSchedule(LocalDate date) {
        List<Order> orders = orderMapper.findActiveOrdersByPickupDate(date);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OrderItem>> itemsByOrder = orderMapper
            .findItemsByOrderIds(orders.stream().map(Order::getId).toList()).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
            .map(order -> new PickupScheduleView(
                order.getId(), order.getOrderNumber(),
                summarize(itemsByOrder.getOrDefault(order.getId(), List.of())),
                order.getStatus(), order.getPickupAt()))
            .toList();
    }

    /** 최신 주문. 관리자 목록과 같은 View를 쓰므로 회원 닉네임 조합도 그대로 재사용한다. */
    @Transactional(readOnly = true)
    public List<OrderListView> getRecentOrders(int limit) {
        return orderAdminService.toViews(orderMapper.findRecentOrders(limit));
    }

    /** 현재 그 상태에 머물러 있는 주문 수(기간 지표가 아닌 스냅샷). */
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return orderMapper.countByStatus(status);
    }

    private String summarize(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "-";
        }
        String first = items.getFirst().getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    /** 종료일 경계는 포함이므로 다음 날 00:00을 반열린 상한으로 쓴다. */
    private LocalDateTime endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }
}
