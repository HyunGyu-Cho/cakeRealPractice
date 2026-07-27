package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.view.MyOrderOverviewView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.service.StoreService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceMyPageTests {

    @Mock private OrderMapper orderMapper;
    @Mock private CartService cartService;
    @Mock private ProductService productService;
    @Mock private StoreService storeService;
    @Mock private NotificationService notificationService;
    @Mock private CouponService couponService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderMapper, cartService, productService, storeService, notificationService,
            couponService,
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void loadsOwnedOrderSummariesWithActualIdsAndProducts() {
        Order ongoing = order(21L, "ORD-ONGOING", "IN_PRODUCTION", null);
        Order recent = order(34L, "ORD-RECENT", "PICKED_UP", 42_000L);
        when(orderMapper.findOngoingByMemberId(7L, 5)).thenReturn(List.of(ongoing));
        when(orderMapper.findRecentCompletedByMemberId(7L, 5)).thenReturn(List.of(recent));
        when(orderMapper.findItemsByOrderIds(new LinkedHashSet<>(List.of(21L, 34L))))
            .thenReturn(List.of(item(21L, "레터링 케이크", "NORMAL"), item(34L, "딸기 케이크", "NORMAL")));

        MyOrderOverviewView result = orderService.getMyOrderOverview(7L);

        assertThat(result.ongoingOrders()).singleElement().satisfies(order -> {
            assertThat(order.id()).isEqualTo(21L);
            assertThat(order.productSummary()).isEqualTo("레터링 케이크");
            assertThat(order.statusLabel()).isEqualTo("제작 중");
            assertThat(order.detailUrl()).isEqualTo("/orders/21");
        });
        assertThat(result.recentOrders()).singleElement().satisfies(order -> {
            assertThat(order.id()).isEqualTo(34L);
            assertThat(order.finalAmount()).isEqualTo(42_000L);
            assertThat(order.statusLabel()).isEqualTo("픽업 완료");
        });
    }

    /** 주문제작은 상세 화면이 /orders/custom/** 로 따로다. 일반 주문 상세로 보내면 안 된다. */
    @Test
    void customOrderLinksToCustomDetail() {
        Order custom = order(55L, "CUS-20260727-AAA", "UNDER_REVIEW", null);
        when(orderMapper.findOngoingByMemberId(7L, 5)).thenReturn(List.of(custom));
        when(orderMapper.findRecentCompletedByMemberId(7L, 5)).thenReturn(List.of());
        when(orderMapper.findItemsByOrderIds(new LinkedHashSet<>(List.of(55L))))
            .thenReturn(List.of(item(55L, "주문 제작 케이크", "CUSTOM")));

        MyOrderOverviewView result = orderService.getMyOrderOverview(7L);

        assertThat(result.ongoingOrders()).singleElement().satisfies(order -> {
            assertThat(order.custom()).isTrue();
            assertThat(order.detailUrl()).isEqualTo("/orders/custom/55");
            assertThat(order.statusLabel()).isEqualTo("확인 중");
        });
    }

    private Order order(Long id, String orderNumber, String status, Long finalAmount) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(orderNumber);
        order.setMemberId(7L);
        order.setStatus(status);
        order.setFinalAmount(finalAmount);
        return order;
    }

    private OrderItem item(Long orderId, String productName, String productType) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductName(productName);
        item.setProductType(productType);
        return item;
    }
}
