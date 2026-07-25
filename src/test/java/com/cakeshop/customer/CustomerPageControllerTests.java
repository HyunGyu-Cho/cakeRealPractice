package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.coupon.controller.CouponController;
import com.cakeshop.domain.home.controller.HomeController;
import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.notification.controller.NotificationController;
import com.cakeshop.domain.order.controller.OrderController;
import com.cakeshop.domain.payment.controller.PaymentController;
import com.cakeshop.domain.review.controller.ReviewController;
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
        // member(로그인·가입·마이페이지)·product(목록·상세) 화면은 실제 구현으로 전환되어 이 목업 스모크 테스트에서 제외한다.
        mockMvc = MockMvcBuilders.standaloneSetup(
            new HomeController(mock(HomeService.class)),
            new OrderController(), new PaymentController(),
            new NotificationController(), new CouponController(), new ReviewController()
        ).build();

        pages.put("/screens", "home/screens");
        pages.put("/orders/pickup", "customer/order/pickup-setting");
        pages.put("/orders/custom/options", "customer/order/custom-option");
        pages.put("/orders/custom/request", "customer/order/custom-request");
        pages.put("/orders/checkout", "customer/order/form");
        pages.put("/orders/1/payment", "customer/payment/form");
        pages.put("/orders/complete", "customer/order/complete");
        pages.put("/orders/1", "customer/order/detail");
        pages.put("/notifications", "customer/notification/list");
        pages.put("/reviews/new", "customer/review/form");
        pages.put("/mypage/coupons", "customer/coupon/list");
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
