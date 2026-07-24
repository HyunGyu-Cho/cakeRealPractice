package com.cakeshop.domain.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpireScheduler {

    // PENDING_PAYMENT 중 결제 제한 시간 초과 주문을 EXPIRED로 전이 (lazy 판정 금지)
    @Scheduled(fixedDelayString = "60000")
    public void expireOverdueOrders() {
        // TODO
    }
}
