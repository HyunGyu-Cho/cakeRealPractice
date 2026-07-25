package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.form.OrderSearchForm;
import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderAdminService {
    private final OrderMapper orderMapper;
    private final MemberService memberService;

    public OrderAdminService(OrderMapper orderMapper, MemberService memberService) {
        this.orderMapper = orderMapper;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public PageResult<OrderListView> getOrderPage(
        OrderSearchForm cond, PageRequest pageRequest) {
        boolean memberFilter = cond.normalizedMemberKeyword() != null;
        List<Long> memberIds = memberFilter
            ? memberService.searchMemberIds(cond.normalizedMemberKeyword())
            : List.of();
        long total = orderMapper.countOrders(cond, memberIds, memberFilter);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<Order> orders = orderMapper.findOrderPage(
            cond, memberIds, memberFilter, pageRequest.getSize(), pageRequest.getOffset());
        return new PageResult<>(toViews(orders), pageRequest, total);
    }

    @Transactional(readOnly = true)
    public List<OrderListView> toViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        Map<Long, List<OrderItem>> itemsByOrder = orderMapper.findItemsByOrderIds(orderIds).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> memberIds = orders.stream().map(Order::getMemberId).collect(Collectors.toSet());
        Map<Long, String> memberNames = memberService.getNicknameMap(memberIds);
        return orders.stream().map(order -> {
            List<OrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of());
            return new OrderListView(
                order.getId(), order.getOrderNumber(), order.getMemberId(),
                memberNames.getOrDefault(order.getMemberId(), "탈퇴 회원"),
                summarize(items), order.getFinalAmount(), order.getStatus(),
                order.getPickupAt(), order.getCreatedAt());
        }).toList();
    }

    private String summarize(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "-";
        }
        String first = items.getFirst().getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
