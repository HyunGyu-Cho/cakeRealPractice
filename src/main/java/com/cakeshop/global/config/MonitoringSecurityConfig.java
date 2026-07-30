package com.cakeshop.global.config;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * monitor 프로필 전용: Actuator 엔드포인트(별도 management 포트 9090)를 인증 없이 연다.
 * <p>
 * SecurityConfig 의 기본 체인은 /actuator/health 만 permitAll 이므로,
 * /actuator/prometheus 등 지표 수집 경로는 이 체인이 담당한다.
 * management.server.port 를 분리했기 때문에 서비스 포트(8080)로는 여전히 지표가 노출되지 않는다.
 * 운영에서는 이 프로필을 켤 때 반드시 9090 포트를 내부망(Prometheus 서버)에만 허용해야 한다.
 */
@Configuration
@Profile("monitor")
public class MonitoringSecurityConfig {

    @Bean
    @Order(0) // 기본 체인보다 먼저 매칭되어야 actuator 요청이 로그인으로 흐르지 않는다.
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
