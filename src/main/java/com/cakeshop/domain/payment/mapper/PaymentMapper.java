package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
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
}
