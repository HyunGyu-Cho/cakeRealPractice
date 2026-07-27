package com.cakeshop.domain.payment.entity;

/**
 * 웹훅 반영 상태. 결제 상태({@link PaymentStatus})와 별개로 "이 이벤트를 처리했는가"만 담는다.
 *
 * <p>{@code SKIPPED}는 실패가 아니다 — 우리 상태가 이미 최신이라 바꿀 것이 없었다는 뜻이다.
 * 승인 결과는 확정 단계가 먼저 반영하므로 정상 흐름에서도 자주 나온다.
 */
public enum WebhookProcessStatus {
    RECEIVED, PROCESSED, SKIPPED, FAILED
}
