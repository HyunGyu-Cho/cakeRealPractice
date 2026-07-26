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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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

    /**
     * 목업 번들 3개는 새 목업을 이관할 때를 위해 파일로 남겨 두지만,
     * 실구현 화면이 다시 끌어다 쓰면 안 된다. JS 번들 2개는 모두 [data-confirm] 핸들러를
     * 갖고 있어 app.js와 함께 로드되면 확인창이 두 번 뜬다.
     * customer-mockup.css는 app.css의 부분집합이면서 :root를 재선언하므로,
     * app.css 뒤에 로드되면 디자인 토큰 팔레트를 통째로 되돌린다.
     */
    @Test
    void noTemplateDependsOnMockupBundle() throws IOException {
        assertNoTemplateReferences("customer-mockup.js");
        assertNoTemplateReferences("admin-mockup.js");
        assertNoTemplateReferences("customer-mockup.css");
    }

    /**
     * app.js는 화면마다 붙이지 않고 공통 프래그먼트(고객 head·관리자 header)에서만 로드한다.
     * 같은 스크립트를 두 번 붙이면 클릭 핸들러가 두 번 등록돼 확인창이 두 번 뜬다.
     */
    @Test
    void adminTemplatesLoadAppScriptOnlyThroughTheSharedHeader() throws IOException {
        Path adminRoot = Path.of("src", "main", "resources", "templates", "admin");
        try (Stream<Path> templates = Files.walk(adminRoot)) {
            List<String> offenders = templates
                .filter(path -> path.toString().endsWith(".html"))
                .filter(path -> readTemplate(path).contains("/js/app.js"))
                .map(Path::toString)
                .toList();

            assertThat(offenders)
                .as("관리자 화면은 app.js를 직접 로드하지 않는다 — fragments/admin/header가 담당한다")
                .isEmpty();
        }
    }

    private void assertNoTemplateReferences(String script) throws IOException {
        Path templateRoot = Path.of("src", "main", "resources", "templates");
        try (Stream<Path> templates = Files.walk(templateRoot)) {
            List<String> offenders = templates
                .filter(path -> path.toString().endsWith(".html"))
                .filter(path -> readTemplate(path).contains(script))
                .map(templateRoot::relativize)
                .map(Path::toString)
                .toList();

            assertThat(offenders)
                .as("화면이 전부 실구현이므로 %s에 의존하면 안 된다", script)
                .isEmpty();
        }
    }

    private String readTemplate(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
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
