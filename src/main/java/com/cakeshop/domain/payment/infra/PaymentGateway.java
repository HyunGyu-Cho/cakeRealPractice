package com.cakeshop.domain.payment.infra;

/**
 * 결제 제공자 경계. 도메인 서비스는 이 인터페이스만 알고, 모의 결제와 토스 실결제는
 * 구현 교체로 갈린다(`cakeshop.payment.provider`).
 *
 * <p>구현체는 절대 DB를 건드리지 않는다. 외부 호출 결과만 돌려주고, 저장은 호출한 서비스가 한다.
 * 그래야 외부 I/O를 DB 트랜잭션 밖에 둘 수 있다.
 */
public interface PaymentGateway {

    /** 결제 화면이 어느 방식으로 결제를 시작해야 하는지 알려준다("mock" | "toss"). */
    String provider();

    /** 결제창을 여는 데 필요한 공개 클라이언트 키. 모의 결제는 {@code null}. */
    String clientKey();

    /** 승인. 실패하면 {@code BusinessException(APPROVE_FAILED)}를 던진다. */
    PaymentApproval approve(String paymentKey, String tossOrderId, long amount,
                           String idempotencyKey);

    /** 전액 취소. 실패하면 {@code BusinessException(CANCEL_FAILED)}를 던진다. */
    PaymentCancel cancel(String paymentKey, String reason, String idempotencyKey);

    /** 상태 대조용 조회. 제공자가 모르는 결제면 {@code null}. */
    PaymentSnapshot find(String paymentKey);
}
