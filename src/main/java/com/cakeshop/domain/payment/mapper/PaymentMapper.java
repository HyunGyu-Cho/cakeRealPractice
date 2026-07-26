package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import com.cakeshop.domain.payment.dto.view.SalesTrendPointView;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.global.common.stats.StatsPeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {
    int insertPayment(Payment payment);

    Optional<Payment> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    Optional<Payment> findByOrderId(@Param("orderId") Long orderId);

    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    int cancelPayment(@Param("paymentId") Long paymentId, @Param("currentStatus") String currentStatus);

    int insertCancellation(PaymentCancellation cancellation);

    List<PaymentCancellation> findCancellationsByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentCancellation> findCancellationsByPaymentIds(
        @Param("paymentIds") Collection<Long> paymentIds);

    long countPayments(@Param("cond") PaymentSearchForm cond);

    List<Payment> findPaymentPage(@Param("cond") PaymentSearchForm cond,
                                  @Param("size") int size,
                                  @Param("offset") int offset);

    // ---- 통계 집계 (statistics 도메인이 PaymentStatsService 계약으로만 사용한다) ----
    PaymentStatsView aggregatePaymentStats(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    List<SalesTrendPointView> aggregateSalesTrend(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  @Param("period") StatsPeriod period);

    /** 처리가 남은 취소·환불 요청 수. */
    long countPendingCancellations();
}
