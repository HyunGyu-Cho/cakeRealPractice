package com.cakeshop.domain.payment.infra;

import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.global.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스페이먼츠 승인·취소·조회 API 클라이언트.
 *
 * <p>멱등 키는 호출한 서비스가 정한 결정적 값을 그대로 헤더로 넘긴다. 같은 결제를 두 번 승인하거나
 * 같은 주문을 두 번 취소하는 사고를 우리 DB 제약과 제공자 양쪽에서 막기 위해서다.
 */
@Component
@ConditionalOnProperty(name = "cakeshop.payment.provider", havingValue = TossPaymentClient.PROVIDER)
public class TossPaymentClient implements PaymentGateway {
    public static final String PROVIDER = "toss";

    private final TossProperties properties;
    private final RestClient restClient;

    @Autowired
    public TossPaymentClient(TossProperties properties, RestClient.Builder builder) {
        this(properties, builder
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory(properties))
            .build());
    }

    /** 테스트에서 요청 팩토리를 갈아끼우기 위한 생성자(`CheckoutPaymentProcessor`의 Clock과 같은 패턴). */
    public TossPaymentClient(TossProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static ClientHttpRequestFactory requestFactory(TossProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        // 승인은 카드사 응답을 기다린다. 여기서 끊기면 "승인됐는데 우리는 모르는" 상태가 되므로 짧게 잡지 않는다.
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String clientKey() {
        return properties.getClientKey();
    }

    @Override
    public PaymentApproval approve(String paymentKey, String tossOrderId, long amount,
                                   String idempotencyKey) {
        TossPayment response = call(PaymentErrorCode.APPROVE_FAILED, () -> restClient.post()
            .uri("/v1/payments/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicAuth(properties.getSecretKey()))
            .header("Idempotency-Key", idempotencyKey)
            .body(Map.of("paymentKey", paymentKey, "orderId", tossOrderId, "amount", amount))
            .retrieve()
            .body(TossPayment.class));

        return new PaymentApproval(response.paymentKey(), response.orderId(), response.totalAmount(),
            response.status(), toLocalDateTime(response.approvedAt()));
    }

    @Override
    public PaymentCancel cancel(String paymentKey, String reason, String idempotencyKey) {
        TossPayment response = call(PaymentErrorCode.CANCEL_FAILED, () -> restClient.post()
            .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicAuth(properties.getSecretKey()))
            .header("Idempotency-Key", idempotencyKey)
            .body(Map.of("cancelReason", reason))
            .retrieve()
            .body(TossPayment.class));

        return new PaymentCancel(response.lastTransactionKey(), response.status(),
            LocalDateTime.now());
    }

    @Override
    public PaymentSnapshot find(String paymentKey) {
        TossPayment response = call(PaymentErrorCode.PAYMENT_FAILED, () -> restClient.get()
            .uri("/v1/payments/{paymentKey}", paymentKey)
            .header(HttpHeaders.AUTHORIZATION, basicAuth(properties.getSecretKey()))
            .retrieve()
            .body(TossPayment.class));

        return new PaymentSnapshot(response.paymentKey(), response.orderId(),
            response.totalAmount(), response.status());
    }

    /** HTTP 오류·타임아웃·응답 없음을 도메인 예외로 좁힌다. 원인 스택은 보존한다. */
    private TossPayment call(PaymentErrorCode errorCode, Supplier<TossPayment> request) {
        TossPayment response;
        try {
            response = request.get();
        } catch (RestClientException e) {
            throw new BusinessException(errorCode, e);
        }
        if (response == null || response.paymentKey() == null) {
            throw new BusinessException(errorCode);
        }
        return response;
    }

    private static String basicAuth(String secretKey) {
        // 토스는 시크릿 키를 사용자명으로, 비밀번호는 빈 값으로 쓴다.
        String raw = (secretKey == null ? "" : secretKey) + ":";
        return "Basic " + Base64.getEncoder()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime toLocalDateTime(String isoOffset) {
        return isoOffset == null ? LocalDateTime.now()
            : OffsetDateTime.parse(isoOffset).toLocalDateTime();
    }

    /** 응답 중 우리가 쓰는 필드만. 나머지는 무시해 스펙이 늘어도 깨지지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPayment(String paymentKey, String orderId, long totalAmount, String status,
                       String approvedAt, String lastTransactionKey) {
    }
}
