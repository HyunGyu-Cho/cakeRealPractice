package com.cakeshop.domain.payment.infra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없이 즉시 승인하는 기본 구현. 테스트·CI·화면 스모크는 전부 이 경로로 돈다.
 *
 * <p>결제창이 없으므로 화면은 폼 제출로 바로 확정 단계를 호출한다.
 */
@Component
@ConditionalOnProperty(name = "cakeshop.payment.provider", havingValue = "mock",
    matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {
    public static final String PROVIDER = "mock";

    private final Clock clock;

    public MockPaymentGateway() {
        this(Clock.systemDefaultZone());
    }

    public MockPaymentGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String clientKey() {
        return null;
    }

    @Override
    public PaymentApproval approve(String paymentKey, String tossOrderId, long amount,
                                   String idempotencyKey) {
        // 모의 결제에는 결제창이 없어 결제 키가 콜백으로 오지 않는다. 여기서 만들어 준다.
        String key = paymentKey == null ? "MOCK-" + UUID.randomUUID() : paymentKey;
        return new PaymentApproval(key, tossOrderId, amount, "MOCK_DONE", LocalDateTime.now(clock));
    }

    @Override
    public PaymentCancel cancel(String paymentKey, String reason, String idempotencyKey) {
        return new PaymentCancel("MOCK-CANCEL-" + UUID.randomUUID(), "MOCK_CANCELED",
            LocalDateTime.now(clock));
    }

    @Override
    public PaymentSnapshot find(String paymentKey) {
        // 대조할 외부 상태가 없다. 배치는 이 경우 아무것도 바꾸지 않는다.
        return null;
    }
}
