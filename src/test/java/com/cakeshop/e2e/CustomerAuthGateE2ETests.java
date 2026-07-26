package com.cakeshop.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 목업 preview 를 끈 <b>운영과 같은 설정</b>에서 화면 잠금 범위를 검증한다.
 *
 * <p>local 프로필은 {@code app.mockup.public-preview=true} 라서 {@code /orders/**} 조회가
 * 비로그인에 열려 있다. 그 상태만 테스트하면 rds 배포에서의 실제 접근 통제를 확인할 수 없으므로,
 * 여기서는 플래그를 false 로 덮어써 별도 컨텍스트로 검증한다({@code CartSecurityTests} 와 같은 방식).
 */
@SpringBootTest(properties = "app.mockup.public-preview=false")
@ActiveProfiles("local")
class CustomerAuthGateE2ETests {

    @Autowired WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/", "/login", "/signup", "/screens", "/products", "/community"
    })
    void 공개_화면은_비로그인으로_열린다(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/cart", "/orders/pickup", "/orders/checkout", "/orders/payment", "/orders/complete",
        "/orders/custom/options", "/mypage", "/mypage/profile", "/mypage/coupons", "/coupons",
        "/reviews", "/reviews/new", "/notifications", "/chat", "/community/new"
    })
    void 회원_전용_화면은_로그인으로_보내진다(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/admin", "/admin/products", "/admin/orders", "/admin/payments", "/admin/members",
        "/admin/coupons", "/admin/reviews", "/admin/community", "/admin/store",
        "/admin/notifications", "/admin/statistics", "/admin/chat"
    })
    void 관리자_화면은_고객_로그인으로_열리지_않는다(String path) throws Exception {
        // 비로그인 → 로그인 화면
        mockMvc.perform(get(path))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        // 고객 로그인 → 403 (로그인 화면으로 되돌리지 않고 권한 부족으로 끊는다)
        mockMvc.perform(get(path).with(user(customer())))
            .andExpect(status().isForbidden());
    }

    /** CSRF 토큰 없는 상태 변경 요청은 거부된다 — 토큰 누락 회귀를 막는다. */
    @ParameterizedTest
    @ValueSource(strings = {"/cart/items", "/cart/items/delete-all", "/orders/checkout", "/reviews"})
    void CSRF_토큰_없는_POST는_거부된다(String path) throws Exception {
        mockMvc.perform(post(path).with(user(customer())))
            .andExpect(status().isForbidden());
    }

    /** 웹훅만 CSRF 예외다 — 예외 범위가 넓어지지 않았는지 확인한다. */
    @Test
    void 토스_웹훅은_CSRF_없이도_403이_아니다() throws Exception {
        int status = mockMvc.perform(post("/webhooks/toss")
                .contentType("application/json").content("{}"))
            .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403);
    }

    /** CSRF 토큰이 붙으면 같은 요청이 컨트롤러까지 도달한다(위 거부가 인증 문제가 아님을 보인다). */
    @Test
    void 고객은_장바구니_POST에_CSRF가_있으면_403이_아니다() throws Exception {
        mockMvc.perform(post("/cart/items").with(user(customer())).with(csrf())
                .param("productId", "0").param("quantity", "1"))
            .andExpect(status().is3xxRedirection());
    }

    private MemberDetails customer() {
        return new MemberDetails(1L, "user@cakeshop.local", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
