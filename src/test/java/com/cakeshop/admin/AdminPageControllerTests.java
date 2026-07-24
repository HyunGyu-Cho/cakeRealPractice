package com.cakeshop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.community.controller.CommunityAdminController;
import com.cakeshop.domain.coupon.controller.CouponAdminController;
import com.cakeshop.domain.member.controller.MemberAdminController;
import com.cakeshop.domain.notification.controller.NotificationAdminController;
import com.cakeshop.domain.order.controller.FulfillmentAdminController;
import com.cakeshop.domain.order.controller.OrderAdminController;
import com.cakeshop.domain.payment.controller.PaymentAdminController;
import com.cakeshop.domain.product.controller.ProductAdminController;
import com.cakeshop.domain.review.controller.ReviewAdminController;
import com.cakeshop.domain.statistics.controller.StatisticsAdminController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminPageControllerTests {

    private MockMvc mockMvc;

    private final Map<String, String> pages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new StatisticsAdminController(), new ProductAdminController(), new OrderAdminController(),
            new FulfillmentAdminController(), new PaymentAdminController(), new CouponAdminController(),
            new MemberAdminController(), new ReviewAdminController(), new NotificationAdminController(),
            new CommunityAdminController()
        ).build();

        pages.put("/admin", "admin/dashboard");
        pages.put("/admin/statistics", "admin/statistics");
        pages.put("/admin/products", "admin/product/list");
        pages.put("/admin/products/new", "admin/product/form");
        pages.put("/admin/products/1/edit", "admin/product/form");
        pages.put("/admin/orders", "admin/order/list");
        pages.put("/admin/orders/1", "admin/order/detail");
        pages.put("/admin/fulfillment", "admin/fulfillment/list");
        pages.put("/admin/payments", "admin/payment/list");
        pages.put("/admin/coupons", "admin/coupon/list");
        pages.put("/admin/members", "admin/member/list");
        pages.put("/admin/reviews", "admin/review/list");
        pages.put("/admin/notifications", "admin/notification/list");
        pages.put("/admin/community", "admin/community/list");
        pages.put("/admin/community/15", "admin/community/detail");
    }

    @Test
    void everyAdminRouteReturnsItsTemplate() throws Exception {
        for (Map.Entry<String, String> page : pages.entrySet()) {
            mockMvc.perform(get(page.getKey()))
                .andExpect(status().isOk())
                .andExpect(view().name(page.getValue()));
        }
    }

    @Test
    void everyAdminViewHasAThymeleafTemplate() {
        pages.values().stream().distinct().forEach(viewName ->
            assertThat(new ClassPathResource("templates/" + viewName + ".html").exists())
                .as("%s 템플릿이 존재해야 한다", viewName)
                .isTrue()
        );
    }
}
