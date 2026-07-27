package com.cakeshop.domain.order.dto.view;

import java.util.List;

public record MyOrderOverviewView(
    List<MyOrderSummaryView> ongoingOrders,
    List<MyOrderSummaryView> recentOrders
) {
}
