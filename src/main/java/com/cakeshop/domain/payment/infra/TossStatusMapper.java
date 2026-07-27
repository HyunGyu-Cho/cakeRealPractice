package com.cakeshop.domain.payment.infra;

import com.cakeshop.domain.payment.entity.PaymentStatus;
import java.util.Locale;

/**
 * 제공자 상태 문자열 → {@link PaymentStatus}. enum 6개는 늘리지 않고 매핑만 한다.
 * 원본 문자열은 {@code payments.provider_status}에 그대로 남으므로 정보가 사라지지 않는다.
 */
public final class TossStatusMapper {

    private TossStatusMapper() {
    }

    /** 모르는 상태는 {@code null}. 호출측이 "건드리지 않는다"로 처리한다. */
    public static PaymentStatus toPaymentStatus(String providerStatus) {
        if (providerStatus == null) {
            return null;
        }
        return switch (providerStatus.trim().toUpperCase(Locale.ROOT)) {
            // 가상계좌 입금 대기도 아직 돈이 들어오지 않은 상태라 READY로 본다.
            case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT", "MOCK_READY" -> PaymentStatus.READY;
            case "DONE", "MOCK_DONE" -> PaymentStatus.DONE;
            case "CANCELED", "MOCK_CANCELED" -> PaymentStatus.CANCELED;
            case "PARTIAL_CANCELED" -> PaymentStatus.PARTIAL_CANCELED;
            case "ABORTED" -> PaymentStatus.ABORTED;
            case "EXPIRED" -> PaymentStatus.EXPIRED;
            default -> null;
        };
    }
}
