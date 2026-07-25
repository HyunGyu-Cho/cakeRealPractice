package com.cakeshop.customer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.mockup.public-preview=true")
@ActiveProfiles("local")
class ScreenRenderingTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void allDatabaseFreePreviewScreensRenderThroughThymeleaf() throws Exception {
        // 관리자 화면은 public-preview 에서도 열리지 않는다(SecurityConfig: /admin/** = ROLE_ADMIN).
        // 따라서 로그인 없이 렌더되는 고객 프리뷰 화면만 검증한다.
        // /mypage·/mypage/profile 은 실구현 전환으로 로그인이 필요해져 프리뷰 대상에서 제외했다.
        // /products/1 은 실구현 전환으로 DB에 해당 상품이 있어야 열려 목록만 남긴다(빈 목록도 200).
        String[] paths = {
            "/screens", "/login", "/signup", "/products",
            "/orders/pickup", "/orders/custom/options", "/orders/custom/request",
            "/orders/checkout", "/orders/1/payment", "/orders/complete",
            "/orders/1", "/notifications", "/reviews/new", "/mypage/coupons"
        };

        for (String path : paths) {
            mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }
}
