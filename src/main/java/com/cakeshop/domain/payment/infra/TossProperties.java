package com.cakeshop.domain.payment.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 토스페이먼츠 접속 설정. 키는 `.env`에서 읽으며 저장소에 값을 커밋하지 않는다. */
@Component
@ConfigurationProperties(prefix = "cakeshop.payment.toss")
public class TossProperties {
    /** 결제창을 여는 공개 키. 화면에 노출된다. */
    private String clientKey;
    /** 승인·취소·조회 API 인증용 비밀 키. 절대 화면에 내보내지 않는다. */
    private String secretKey;
    private String baseUrl = "https://api.tosspayments.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    /** 승인은 카드사 응답을 기다리므로 넉넉해야 한다. */
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
