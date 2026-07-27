package com.cakeshop.domain.payment.dto.view;

/**
 * 결제 준비 결과 — 화면이 결제를 시작하는 데 필요한 값.
 *
 * <p>금액과 주문번호는 서버가 정한 값이며 화면은 그대로 제공자에게 넘기기만 한다.
 * 화면이 바꿔 보내도 확정 단계에서 저장된 금액과 대조해 걸러낸다.
 */
public record PaymentPrepareView(String provider, String clientKey, String tossOrderId,
                                 long amount, String orderName) {

    /** 결제창을 여는 경로인지. 모의 결제는 폼 제출로 바로 확정 단계를 호출한다. */
    public boolean requiresPaymentWindow() {
        return clientKey != null && !clientKey.isBlank();
    }
}
