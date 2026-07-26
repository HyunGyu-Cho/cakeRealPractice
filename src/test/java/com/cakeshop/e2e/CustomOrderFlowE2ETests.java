package com.cakeshop.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 주문제작 시나리오: 고객 요청 → 관리자 검토(견적/반려) → 고객 수락·결제 → 관리자 픽업 처리.
 *
 * <p>일반 주문과 시작 상태가 다르고({@code UNDER_REVIEW}) 상태 전이 규칙도 별개이므로,
 * 두 역할이 번갈아 조작하는 동안 {@code orders.status} 가 규칙대로만 움직이는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CustomOrderFlowE2ETests {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired com.cakeshop.domain.store.service.StoreService storeService;
    @Autowired com.cakeshop.domain.order.service.CustomOrderService customOrderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 요청부터_견적_수락_결제_픽업완료까지_이어진다() throws Exception {
        MemberDetails customer = givenCustomer();
        MemberDetails admin = givenAdmin();
        Long customProductId = customProductId();

        // 1. 옵션 선택 화면 → 요청 접수 (시작 상태는 UNDER_REVIEW)
        mockMvc.perform(get("/orders/custom/options").with(user(customer))
                .param("productId", customProductId.toString()))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-option"))
            .andExpect(model().attributeExists("product", "optionGroups", "customOrderForm"));

        LocalDateTime desiredPickupAt = firstAvailableSlot(customProductId);
        Long orderId = submitRequest(customer, customProductId, desiredPickupAt);
        assertThat(orderStatus(orderId)).isEqualTo("UNDER_REVIEW");

        // 2. 고객 상세 화면, 그리고 orderId 없는 목업 URL 은 최신 요청으로 보낸다
        mockMvc.perform(get("/orders/custom/{id}", orderId).with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-request"))
            .andExpect(model().attributeExists("request", "cancelForm"));
        mockMvc.perform(get("/orders/custom/request").with(user(customer)))
            .andExpect(redirectedUrl("/orders/custom/" + orderId));

        // 3. 관리자 검토 목록·상세 → 견적 발송 (제작 가능일은 픽업일 이전이어야 한다)
        mockMvc.perform(get("/admin/custom-orders").with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/order/custom-list"));
        mockMvc.perform(get("/admin/custom-orders/{id}", orderId).with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/order/custom-detail"));

        mockMvc.perform(post("/admin/custom-orders/{id}/quote", orderId)
                .with(user(admin)).with(csrf())
                .param("quotedAmount", "95000")
                .param("producibleDate", desiredPickupAt.toLocalDate().toString())
                .param("adminNote", "요청대로 제작 가능합니다."))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
        // 견적을 보내도 아직 검토 단계고, 확정 금액도 그대로다 —
        // 주문의 final_amount 는 고객이 수락할 때 견적 금액으로 덮어쓴다.
        assertThat(orderStatus(orderId)).isEqualTo("UNDER_REVIEW");
        long estimatedAmount = jdbcTemplate.queryForObject(
            "SELECT final_amount FROM orders WHERE id = ?", Long.class, orderId);
        assertThat(estimatedAmount).isNotEqualTo(95_000L);

        // 4. 고객이 견적 수락 → 결제 링크 토큰 발급
        String payRedirect = mockMvc.perform(post("/orders/custom/{id}/accept", orderId)
                .with(user(customer)).with(csrf()))
            .andExpect(redirectedUrlPattern("/orders/custom/pay/*"))
            .andExpect(flash().attribute("successMessage", "견적을 수락했습니다. 결제를 진행해 주세요."))
            .andReturn().getResponse().getRedirectedUrl();
        String token = payRedirect.substring(payRedirect.lastIndexOf('/') + 1);
        // 수락 시점에 견적 금액이 확정 금액이 된다.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT final_amount FROM orders WHERE id = ?", Long.class, orderId))
            .isEqualTo(95_000L);

        mockMvc.perform(get("/orders/custom/pay/{token}", token).with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-payment"))
            .andExpect(model().attributeExists("request", "token", "coupons", "paymentForm"));

        // 5. 결제 → 제작 시작(IN_PRODUCTION)
        mockMvc.perform(post("/orders/custom/pay/{token}", token).with(user(customer)).with(csrf())
                .param("method", "CARD"))
            .andExpect(redirectedUrl("/orders/" + orderId))
            .andExpect(flash().attribute("successMessage", "결제가 완료되었습니다. 제작을 시작합니다."));
        assertThat(orderStatus(orderId)).isEqualTo("IN_PRODUCTION");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE order_id = ?", String.class, orderId)).isEqualTo("DONE");

        // 6. 관리자 픽업 처리 — 준비 완료 → 픽업 완료
        LocalDate pickupDate = jdbcTemplate.queryForObject(
            "SELECT DATE(pickup_at) FROM orders WHERE id = ?", LocalDate.class, orderId);
        mockMvc.perform(get("/admin/fulfillment").with(user(admin))
                .param("pickupDate", pickupDate.toString()))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/fulfillment/list"));

        mockMvc.perform(post("/admin/fulfillment/{id}/status", orderId).with(user(admin)).with(csrf())
                .param("status", "READY_FOR_PICKUP")
                .param("pickupDate", pickupDate.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "픽업 상태를 변경했습니다."));
        assertThat(orderStatus(orderId)).isEqualTo("READY_FOR_PICKUP");

        mockMvc.perform(post("/admin/fulfillment/{id}/status", orderId).with(user(admin)).with(csrf())
                .param("status", "PICKED_UP")
                .param("pickupDate", pickupDate.toString()))
            .andExpect(status().is3xxRedirection());
        assertThat(orderStatus(orderId)).isEqualTo("PICKED_UP");

        // 7. 최종 상태에서는 되돌릴 수 없다 — 전이 규칙은 서비스가 소유한다
        mockMvc.perform(post("/admin/fulfillment/{id}/status", orderId).with(user(admin)).with(csrf())
                .param("status", "READY_FOR_PICKUP")
                .param("pickupDate", pickupDate.toString()))
            .andExpect(status().is4xxClientError());
        assertThat(orderStatus(orderId)).isEqualTo("PICKED_UP");
    }

    @Test
    void 관리자가_반려하면_REJECTED가_되고_고객은_결제로_갈_수_없다() throws Exception {
        MemberDetails customer = givenCustomer();
        MemberDetails admin = givenAdmin();
        Long orderId = givenSubmittedRequest(customer);

        mockMvc.perform(post("/admin/custom-orders/{id}/reject", orderId).with(user(admin)).with(csrf())
                .param("reason", "요청한 디자인은 제작이 어렵습니다."))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
        assertThat(orderStatus(orderId)).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT reject_reason FROM orders WHERE id = ?", String.class, orderId))
            .isEqualTo("요청한 디자인은 제작이 어렵습니다.");

        // 반려된 요청은 견적 수락으로 결제 링크를 얻을 수 없다.
        mockMvc.perform(post("/orders/custom/{id}/accept", orderId).with(user(customer)).with(csrf()))
            .andExpect(status().is4xxClientError());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId)).isZero();
    }

    @Test
    void 고객은_남의_주문제작_요청을_볼_수_없다() throws Exception {
        MemberDetails owner = givenCustomer();
        MemberDetails stranger = givenCustomer();
        Long orderId = givenSubmittedRequest(owner);

        mockMvc.perform(get("/orders/custom/{id}", orderId).with(user(stranger)))
            .andExpect(status().is4xxClientError());
    }

    /** 희망 픽업 일시가 과거면 요청이 접수되지 않고 폼이 재렌더된다. */
    @Test
    void 과거_픽업일시로는_요청이_접수되지_않는다() throws Exception {
        MemberDetails customer = givenCustomer();
        long before = countOrders();

        mockMvc.perform(post("/orders/custom/options").with(user(customer)).with(csrf())
                .param("productId", customProductId().toString())
                .param("pickupAt", LocalDate.now().minusDays(1).atTime(14, 0)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/custom-option"))
            .andExpect(model().attributeHasFieldErrors("customOrderForm", "pickupAt"));

        assertThat(countOrders()).isEqualTo(before);
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    private Long givenSubmittedRequest(MemberDetails customer) throws Exception {
        Long productId = customProductId();
        return submitRequest(customer, productId, firstAvailableSlot(productId));
    }

    /**
     * 주문제작 요청을 접수하고 생성된 주문 id 를 돌려준다.
     * 접수가 실패해 폼이 재렌더되면 바인딩·업무 오류 메시지를 그대로 실패 메시지에 실어 준다.
     */
    private Long submitRequest(MemberDetails customer, Long productId, LocalDateTime pickupAt)
        throws Exception {
        var request = post("/orders/custom/options")
            .with(user(customer)).with(csrf())
            .param("productId", productId.toString())
            .param("lettering", "생일 축하")
            .param("requirements", "E2E 요청사항입니다.")
            .param("pickupAt", pickupAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .param("desiredBudget", "80000");
        // 필수 옵션 그룹마다 첫 옵션을 고른다 — 어떤 상품이 시드에 들어와도 접수가 통과한다.
        for (Long optionId : requiredOptionIds(productId)) {
            request.param("optionIds", optionId.toString());
        }
        var result = mockMvc.perform(request).andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        if (redirect == null) {
            var errors = result.getModelAndView() == null ? null
                : result.getModelAndView().getModel()
                    .get("org.springframework.validation.BindingResult.customOrderForm");
            throw new AssertionError("주문제작 요청 접수가 실패했습니다. pickupAt=" + pickupAt
                + ", errors=" + errors);
        }
        assertThat(redirect).startsWith("/orders/custom/");
        return Long.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
    }

    private List<Long> requiredOptionIds(Long productId) {
        return customOrderService.getOptionGroups(productId).stream()
            .filter(com.cakeshop.domain.product.dto.view.ProductOptionGroupView::required)
            .filter(group -> !group.options().isEmpty())
            .map(group -> group.options().getFirst().id())
            .toList();
    }

    /** 매장 영업시간·휴무일을 반영한 실제 예약 가능 슬롯. 상품 준비일 이후만 후보다. */
    private LocalDateTime firstAvailableSlot(Long productId) {
        int preparationDays = jdbcTemplate.queryForObject(
            "SELECT preparation_days FROM products WHERE id = ?", Integer.class, productId);
        LocalDate from = LocalDate.now().plusDays(preparationDays);
        for (int day = 0; day <= 14 - preparationDays; day++) {
            List<LocalDateTime> slots =
                storeService.getAvailablePickupSlots(from.plusDays(day), preparationDays);
            if (!slots.isEmpty()) {
                return slots.getFirst();
            }
        }
        throw new AssertionError("예약 가능한 주문제작 픽업 슬롯이 없습니다.");
    }

    private Long customProductId() {
        Long existing = jdbcTemplate.query(
            "SELECT id FROM products WHERE product_type = 'CUSTOM' AND status = 'ACTIVE' ORDER BY id LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null);
        if (existing != null) {
            return existing;
        }
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        String name = "E2E주문제작-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
            INSERT INTO products
                (category_id, name, base_price, product_type, preparation_days,
                 cancellation_limit_days, stock_quantity, status)
            VALUES (?, ?, 60000, 'CUSTOM', 3, 3, 0, 'ACTIVE')
            """, categoryId, name);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = ?", Long.class, name);
    }

    private MemberDetails givenCustomer() {
        String email = "e2e-custom-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("""
            INSERT INTO members (email, password, nickname, phone, role, status)
            VALUES (?, '{noop}x', '주문제작고객', '010-0000-0000', 'USER', 'ACTIVE')
            """, email);
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);
        return new MemberDetails(id, email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MemberDetails givenAdmin() {
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE role = 'ADMIN' ORDER BY id LIMIT 1", Long.class);
        return new MemberDetails(id, "admin@cakeshop.local", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private long countOrders() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
    }
}
