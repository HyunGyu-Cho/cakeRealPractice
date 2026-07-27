package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.infra.PaymentCancel;
import com.cakeshop.domain.payment.infra.PaymentGateway;
import com.cakeshop.domain.payment.service.RefundProcessor.CancelTarget;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 취소·환불의 경계. 승인과 같은 이유로 외부 취소 호출은 DB 트랜잭션 밖에서 한다.
 *
 * <p>순서는 검증 → 외부 취소 → 확정이다. 외부 취소가 실패하면 우리 DB는 아무것도 바꾸지 않으므로
 * 재고가 돌아왔는데 환불은 안 된 상태가 생기지 않는다.
 */
@Service
public class RefundService {
    private final RefundProcessor processor;
    private final PaymentGateway gateway;

    public RefundService(RefundProcessor processor, PaymentGateway gateway) {
        this.processor = processor;
        this.gateway = gateway;
    }

    public void cancelByCustomer(Long memberId, Long orderId, String reason) {
        cancel(memberId, orderId, reason, false);
    }

    public void cancelByAdmin(Long orderId, String reason) {
        cancel(null, orderId, reason, true);
    }

    private void cancel(Long memberId, Long orderId, String reason, boolean admin) {
        Optional<CancelTarget> target = processor.validate(memberId, orderId, admin);
        if (target.isEmpty()) {
            // 이미 취소된 주문이다. 외부 취소도 부르지 않는다.
            return;
        }
        // 같은 결정적 키를 제공자에게도 넘겨 이중 취소를 양쪽에서 막는다.
        PaymentCancel canceled = gateway.cancel(target.get().paymentKey(), reason.trim(),
            RefundProcessor.cancelIdempotencyKey(orderId));
        processor.confirm(memberId, orderId, reason, admin, canceled);
    }
}
