package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.infra.PaymentSnapshot;
import com.cakeshop.domain.payment.infra.TossStatusMapper;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 결제창을 닫아버려 {@code READY}로 남은 결제를 제공자 상태와 대조해 정리한다.
 *
 * <p>결제 키가 없는 준비 행은 제공자에게 물어볼 것이 없으므로 {@code EXPIRED}로 마감한다.
 * 결제 키가 있는데 제공자 쪽이 {@code DONE}이면 승인은 됐는데 우리 확정이 유실된 경우다 —
 * 자동으로 주문을 만들지는 않고 상태만 맞춰 사람이 볼 수 있게 남긴다(부분 자동화의 경계).
 */
@Service
public class PaymentReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final int BATCH_SIZE = 100;

    private final PaymentMapper paymentMapper;
    private final PaymentGateway gateway;
    private final int staleMinutes;
    private final Clock clock;

    @Autowired
    public PaymentReconciliationService(PaymentMapper paymentMapper, PaymentGateway gateway,
                                        @Value("${cakeshop.payment.reconcile.stale-minutes:30}")
                                        int staleMinutes) {
        this(paymentMapper, gateway, staleMinutes, Clock.systemDefaultZone());
    }

    public PaymentReconciliationService(PaymentMapper paymentMapper, PaymentGateway gateway,
                                        int staleMinutes, Clock clock) {
        this.paymentMapper = paymentMapper;
        this.gateway = gateway;
        this.staleMinutes = staleMinutes;
        this.clock = clock;
    }

    /** @return 상태를 바꾼 결제 수 */
    public int reconcileStaleReady() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMinutes(staleMinutes);
        List<Payment> targets = paymentMapper.findStaleReady(threshold, BATCH_SIZE);
        int changed = 0;
        for (Payment payment : targets) {
            try {
                changed += reconcileOne(payment) ? 1 : 0;
            } catch (RuntimeException e) {
                // 한 건 실패가 나머지를 멈추게 하지 않는다. 다음 주기에 다시 집힌다.
                log.warn("결제 상태 대조에 실패했습니다. paymentId={}", payment.getId(), e);
            }
        }
        return changed;
    }

    private boolean reconcileOne(Payment payment) {
        if (payment.getPaymentKey() == null) {
            // 결제창을 열지도 않고 떠난 준비 행이다. 제공자에게 물어볼 것이 없다.
            return paymentMapper.abortPayment(payment.getId(), PaymentStatus.EXPIRED.name(),
                null, PaymentErrorCode.NOT_READY.code(), "결제 시작 후 응답 없음") == 1;
        }

        PaymentSnapshot snapshot = gateway.find(payment.getPaymentKey());
        if (snapshot == null) {
            // 모의 결제이거나 제공자가 모르는 결제다. 함부로 바꾸지 않는다.
            return false;
        }
        PaymentStatus actual = TossStatusMapper.toPaymentStatus(snapshot.providerStatus());
        if (actual == null || actual == PaymentStatus.READY) {
            return false;
        }
        if (actual == PaymentStatus.DONE) {
            log.warn("승인됐지만 주문 확정이 유실된 결제입니다. 수동 확인 필요. paymentId={}, paymentKey={}",
                payment.getId(), payment.getPaymentKey());
        }
        return paymentMapper.syncStatus(payment.getId(), PaymentStatus.READY.name(),
            actual.name(), snapshot.providerStatus()) == 1;
    }
}
