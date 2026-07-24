package com.cakeshop.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean publicPreview;

    public SecurityConfig(@Value("${app.mockup.public-preview:false}") boolean publicPreview) {
        this.publicPreview = publicPreview;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 웹훅 경로만 CSRF 제외 — 전체 비활성화 금지
            .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/toss"))
            .authorizeHttpRequests(auth -> {
                // ① 공개 GET을 먼저 선언 (matcher 순서 = 우선순위)
                auth.requestMatchers("/", "/login", "/signup", "/products/**", "/cart", "/screens",
                        "/favicon.ico", "/css/**", "/js/**", "/images/**", "/uploads/**", "/error").permitAll();
                // 로드밸런서/헬스체크가 인증 없이 호출할 수 있도록 허용 (그 외 actuator 엔드포인트는 미노출)
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/community", "/community/{id:\\d+}",
                        "/community/scroll", "/community/api/posts").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/webhooks/toss").permitAll();

                if (publicPreview) {
                    // local 프로필에서만 고객 목업 흐름을 로그인 없이 확인한다.
                    // 관리자 화면은 preview에서도 열지 않는다 — 아래 /admin/** 규칙에 따라 관리자 로그인이 필요하다.
                    auth.requestMatchers(HttpMethod.GET,
                        "/orders/**", "/mypage/**", "/notifications", "/reviews/**", "/community/new", "/chat").permitAll();
                }

                // ② 관리자. 모든 관리자 화면은 관리자 로그인을 요구한다.
                auth.requestMatchers("/admin/**").hasRole("ADMIN");
                // ③ 나머지는 로그인 회원
                auth.anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                // 화면과 도메인 모두 이메일을 로그인 식별자로 사용한다.
                .usernameParameter("email")
                .failureUrl("/login?error")
                // 역할별 기본 진입점 분기: 관리자 → /admin, 고객 → /mypage (저장된 요청이 있으면 그 경로 우선)
                .successHandler(new RoleBasedAuthenticationSuccessHandler())
                .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
