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
        // 실제 구현된 member·product·cart·일반 order/payment·chat·notification·주문제작 화면은
        // 인증 또는 DB가 필요하므로 목업 프리뷰 대상에서 제외한다.
        String[] paths = {
            "/screens", "/login",
            "/reviews/new", "/mypage/coupons"
        };

        for (String path : paths) {
            mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }
}
