package com.cakeshop.domain.payment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentClientTests {
    private static final String SECRET = "test_sk_secret";

    private MockRestServiceServer server;
    private TossPaymentClient client;

    @BeforeEach
    void setUp() {
        TossProperties properties = new TossProperties();
        properties.setBaseUrl("https://api.tosspayments.com");
        properties.setSecretKey(SECRET);
        properties.setClientKey("test_ck_client");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentClient(properties, builder.build());
    }

    private String expectedAuth() {
        return "Basic " + Base64.getEncoder()
            .encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void approveSendsSecretKeyAndIdempotencyKeyAndMapsTheResponse() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", expectedAuth()))
            .andExpect(header("Idempotency-Key", "checkout-1"))
            .andExpect(jsonPath("$.paymentKey").value("PK-1"))
            .andExpect(jsonPath("$.orderId").value("ORD-1"))
            .andExpect(jsonPath("$.amount").value(82_000))
            .andRespond(withSuccess("""
                {"paymentKey":"PK-1","orderId":"ORD-1","totalAmount":82000,"status":"DONE",
                 "approvedAt":"2026-07-27T14:00:00+09:00"}
                """, MediaType.APPLICATION_JSON));

        PaymentApproval approval = client.approve("PK-1", "ORD-1", 82_000L, "checkout-1");

        assertThat(approval.paymentKey()).isEqualTo("PK-1");
        assertThat(approval.amount()).isEqualTo(82_000L);
        assertThat(approval.providerStatus()).isEqualTo("DONE");
        assertThat(approval.approvedAt()).isEqualTo("2026-07-27T14:00:00");
        server.verify();
    }

    @Test
    void cancelSendsReasonAndReturnsTransactionKey() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/PK-1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "CANCEL-ORDER-9"))
            .andExpect(content().json("{\"cancelReason\":\"일정 변경\"}"))
            .andRespond(withSuccess("""
                {"paymentKey":"PK-1","orderId":"ORD-1","totalAmount":82000,"status":"CANCELED",
                 "lastTransactionKey":"TX-9"}
                """, MediaType.APPLICATION_JSON));

        PaymentCancel canceled = client.cancel("PK-1", "일정 변경", "CANCEL-ORDER-9");

        assertThat(canceled.transactionKey()).isEqualTo("TX-9");
        assertThat(canceled.providerStatus()).isEqualTo("CANCELED");
        server.verify();
    }

    /** 외부 장애 문구를 그대로 노출하지 않고 도메인 오류로 좁힌다. 원인 스택은 보존한다. */
    @Test
    void httpErrorBecomesDomainErrorWithCausePreserved() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.approve("PK-1", "ORD-1", 82_000L, "checkout-1"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.APPROVE_FAILED)
            .hasCauseInstanceOf(RuntimeException.class);
    }
}
