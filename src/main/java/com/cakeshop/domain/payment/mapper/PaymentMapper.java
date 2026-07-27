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

    Optional<Payment> findByTossOrderId(@Param("tossOrderId") String tossOrderId);

    Optional<Payment> findByTossOrderIdForUpdate(@Param("tossOrderId") String tossOrderId);

    /** 준비된 READY 결제의 금액을 최신 재계산값으로 맞춘다(쿠폰·가격이 그 사이 바뀐 경우). */
    int updateReadyAmount(@Param("paymentId") Long paymentId, @Param("amount") Long amount);

    /** 승인 결과를 READY 행에 확정한다. status='READY' 조건이 재확정을 막는다. */
    int confirmPayment(Payment payment);

    /** 결제창 실패·이탈·보상 취소로 마감. {@code status}는 ABORTED 또는 EXPIRED다. */
    int abortPayment(@Param("paymentId") Long paymentId, @Param("status") String status,
                     @Param("providerStatus") String providerStatus,
                     @Param("failureCode") String failureCode,
                     @Param("failureMessage") String failureMessage);

    /** 웹훅·대조 배치가 외부 상태를 따라잡을 때. */
    int syncStatus(@Param("paymentId") Long paymentId, @Param("currentStatus") String currentStatus,
                   @Param("status") String status, @Param("providerStatus") String providerStatus);

    /** 결제창을 닫아버려 READY로 남은 결제. 대조 배치가 꺼내 간다. */
    List<Payment> findStaleReady(@Param("threshold") LocalDateTime threshold,
                                 @Param("limit") int limit);

    int cancelPayment(@Param("paymentId") Long paymentId, @Param("currentStatus") String currentStatus,
                      @Param("providerStatus") String providerStatus);

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
