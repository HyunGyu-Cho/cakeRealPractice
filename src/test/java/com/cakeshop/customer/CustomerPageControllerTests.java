package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.home.controller.HomeController;
import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.order.controller.OrderController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerPageControllerTests {

    private MockMvc mockMvc;
    private final Map<String, String> pages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        // 고객 화면은 전부 실구현으로 전환됐고 각 도메인 전용 테스트가 담당한다.
        // 이 스모크에는 도메인 서비스가 필요 없는 안내 화면(/screens)만 남는다.
        mockMvc = MockMvcBuilders.standaloneSetup(
            new HomeController(mock(HomeService.class)),
            new OrderController()
        ).build();

        pages.put("/screens", "home/screens");
    }

    @Test
    void everyCustomerRouteReturnsItsTemplate() throws Exception {
        for (Map.Entry<String, String> page : pages.entrySet()) {
            mockMvc.perform(get(page.getKey()))
                .andExpect(status().isOk())
                .andExpect(view().name(page.getValue()));
        }
    }

    @Test
    void everyCustomerViewAndMockupAssetExists() {
        pages.values().stream().distinct().forEach(viewName ->
            assertThat(new ClassPathResource("templates/" + viewName + ".html").exists())
                .as("%s template must exist", viewName)
                .isTrue()
        );
        assertThat(new ClassPathResource("static/css/customer-mockup.css").exists()).isTrue();
        assertThat(new ClassPathResource("static/js/customer-mockup.js").exists()).isTrue();
    }

    @Test
    void cartTemplateUsesServerDataAndDedicatedBehavior() throws IOException {
        String cartTemplate = new ClassPathResource("templates/customer/cart/list.html")
            .getContentAsString(StandardCharsets.UTF_8);
        String cartScript = new ClassPathResource("static/js/cart.js")
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(cartTemplate)
            .contains("th:each=\"item : ${cart.items}\"", "data-cart-root", "/cart/checkout")
            .doesNotContain("fragments/customer/mock-notice", "customer-mockup.js");
        assertThat(cartScript).contains("data-cart-item-check").doesNotContain("localStorage");
    }
}
