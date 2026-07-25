package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
    private final OrderMapper orderMapper;
    private final OrderAdminService orderAdminService;
    private final OrderService orderService;

    public FulfillmentService(OrderMapper orderMapper, OrderAdminService orderAdminService,
                              OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderAdminService = orderAdminService;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public List<OrderListView> getOrders(LocalDate pickupDate) {
        List<Order> orders = orderMapper.findGeneralOrdersByPickupDate(pickupDate);
        return orderAdminService.toViews(orders);
    }

    @Transactional
    public void markReady(Long orderId) {
        orderService.transition(orderId, OrderStatus.READY_FOR_PICKUP);
    }

    @Transactional
    public void markPickedUp(Long orderId) {
        orderService.transition(orderId, OrderStatus.PICKED_UP);
    }
}
