package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import com.cakeshop.domain.payment.dto.view.SalesTrendPointView;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.common.stats.StatsPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * payment 도메인이 statistics에 공개하는 집계 계약. 매출의 정본은 이 도메인이다 —
 * 주문 금액(order)과 달리 실제 승인된 결제에서 환불을 뺀 순매출을 계산한다.
 */
@Service
public class PaymentStatsService {
    private final PaymentMapper paymentMapper;

    public PaymentStatsService(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Transactional(readOnly = true)
    public PaymentStatsView getPaymentStats(LocalDate from, LocalDate to) {
        PaymentStatsView stats = paymentMapper.aggregatePaymentStats(startOf(from), endOf(to));
        return stats == null ? PaymentStatsView.empty() : stats;
    }

    @Transactional(readOnly = true)
    public List<SalesTrendPointView> getSalesTrend(
        LocalDate from, LocalDate to, StatsPeriod period) {
        return paymentMapper.aggregateSalesTrend(startOf(from), endOf(to), period);
    }

    @Transactional(readOnly = true)
    public long countPendingCancellations() {
        return paymentMapper.countPendingCancellations();
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    /** 종료일 경계는 포함이므로 다음 날 00:00을 반열린 상한으로 쓴다. */
    private LocalDateTime endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }
}
