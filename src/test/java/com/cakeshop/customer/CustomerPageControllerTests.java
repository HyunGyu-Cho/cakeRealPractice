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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * app.js를 로드하는 곳은 fragments/common/head 하나뿐이어야 한다.
     * 예전에는 고객 화면이 head 프래그먼트로, 관리자 화면이 admin/header로 각각 로드했는데
     * 둘 다 쓰는 관리자 화면 4개(픽업 처리·주문 목록/상세·결제 관리)에서 같은 파일이 두 번 실행됐다.
     * app.js는 document에 click/change/submit 리스너를 거는 구조라 핸들러가 2벌 등록되고,
     * 그 화면에 [data-confirm] 버튼을 하나 넣는 순간 확인창이 두 번 뜬다.
     */
    @Test
    void appScriptIsLoadedByTheHeadFragmentOnly() throws IOException {
        Path templateRoot = Path.of("src", "main", "resources", "templates");
        Path headFragment = templateRoot.resolve(Path.of("fragments", "common", "head.html"));

        try (Stream<Path> templates = Files.walk(templateRoot)) {
            List<String> offenders = templates
                .filter(path -> path.toString().endsWith(".html"))
                .filter(path -> !path.equals(headFragment))
                .filter(path -> readTemplate(path).contains("/js/app.js"))
                .map(templateRoot::relativize)
                .map(Path::toString)
                .toList();

            assertThat(offenders)
                .as("app.js는 fragments/common/head 한 곳에서만 로드한다 — 두 번 실행되면 핸들러가 2벌 등록된다")
                .isEmpty();
        }
        assertThat(readTemplate(headFragment))
            .as("공통 head가 app.js를 실제로 로드해야 한다")
            .contains("/js/app.js");
    }

    /**
     * 모든 화면은 자기 &lt;head&gt;를 직접 쓰지 않고 공통 프래그먼트를 부른다.
     * 직접 쓰면 favicon·viewport·CSRF meta처럼 조용히 빠지는 항목이 생긴다
     * (통일 전 24개 화면에 favicon이 없었고 error 화면 3개는 viewport도 없었다).
     */
    @Test
    void everyScreenUsesTheSharedHeadFragment() throws IOException {
        Path templateRoot = Path.of("src", "main", "resources", "templates");
        try (Stream<Path> templates = Files.walk(templateRoot)) {
            List<String> offenders = templates
                .filter(path -> path.toString().endsWith(".html"))
                .filter(path -> !path.toString().contains("fragments"))
                .filter(path -> !readTemplate(path).contains("fragments/common/head :: head("))
                .map(templateRoot::relativize)
                .map(Path::toString)
                .toList();

            assertThat(offenders)
                .as("화면은 <head>를 직접 쓰지 않고 fragments/common/head를 부른다")
                .isEmpty();
        }
    }

    /**
     * 채팅 스타일의 단일 출처는 chat.css다. app.css는 전 화면에 실리지만 .chat-* 를 쓰는 화면은
     * chat.css를 함께 싣는 채팅 2개뿐이라, app.css에 두면 나중에 로드된 chat.css가 이기는
     * 사문 규칙이 된다. 실제로 두 파일의 말풍선 최대폭·메시지 영역 높이 값이 서로 어긋난 채
     * 방치됐었다. 양방향으로 고정한다 — app.css에 채팅 규칙이 없을 것, 그리고
     * .chat-* 를 쓰는 화면은 chat.css를 반드시 링크할 것.
     */
    @Test
    void chatStylesLiveOnlyInChatCss() throws IOException {
        String appCss = new ClassPathResource("static/css/app.css")
            .getContentAsString(StandardCharsets.UTF_8)
            .replaceAll("(?s)/\\*.*?\\*/", "");

        assertThat(appCss)
            .as("채팅 스타일은 chat.css가 단일 출처다 — app.css에 .chat-* 규칙을 두지 않는다")
            .doesNotContain(".chat-", ".admin-chat-");

        Path templateRoot = Path.of("src", "main", "resources", "templates");
        try (Stream<Path> templates = Files.walk(templateRoot)) {
            List<String> offenders = templates
                .filter(path -> path.toString().endsWith(".html"))
                .filter(path -> {
                    String html = readTemplate(path);
                    return usesChatClass(html) && !html.contains("/css/chat.css");
                })
                .map(templateRoot::relativize)
                .map(Path::toString)
                .toList();

            assertThat(offenders)
                .as(".chat-* 클래스를 쓰는 화면은 chat.css를 함께 링크해야 한다")
                .isEmpty();
        }
    }

    /** class·th:classappend 값에 chat-/admin-chat- 으로 시작하는 클래스가 있는지 본다. */
    private boolean usesChatClass(String html) {
        Matcher attributes = Pattern.compile("(?:th:classappend|class)=\"([^\"]*)\"").matcher(html);
        while (attributes.find()) {
            if (Pattern.compile("(?:^|[\\s'])(?:admin-)?chat-[a-z]").matcher(attributes.group(1)).find()) {
                return true;
            }
        }
        return false;
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
