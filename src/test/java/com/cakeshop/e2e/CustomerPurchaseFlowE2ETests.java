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

import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 고객이 실제로 밟는 화면 순서를 HTTP 요청으로 그대로 재현하는 E2E 테스트.
 *
 * <p>기존 테스트는 서비스 단위(mock mapper) 또는 화면 단건 렌더였다. 여기서는 회원가입부터
 * 결제·취소까지 <b>여러 도메인을 가로지르는 한 세션</b>을 검증한다 — 리다이렉트 체인,
 * 세션에 실린 {@link CheckoutDraft}, 그리고 실제 MariaDB 의 최종 상태까지 함께 본다.
 *
 * <p>{@code @Transactional} 이므로 테스트가 끝나면 모든 삽입이 롤백된다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CustomerPurchaseFlowE2ETests {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StoreService storeService;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }
        return mockMvc;
    }

    @Test
    void 회원가입부터_결제_취소까지_한_세션으로_이어진다() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "e2e-" + suffix + "@test.local";

        // ── 1. 회원가입 화면 → 가입 → 로그인 화면으로 리다이렉트
        mockMvc().perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/signup"))
            .andExpect(model().attributeExists("signupForm"));

        mockMvc().perform(post("/signup").with(csrf())
                .param("email", email)
                .param("password", "Passw0rd!")
                .param("passwordConfirm", "Passw0rd!")
                .param("nickname", "E2E고객-" + suffix)
                .param("phone", "010-1234-5678")
                .param("termsService", "true")
                .param("termsPrivacy", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요."));

        Long memberId = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM members WHERE id = ?", String.class, memberId)).isEqualTo("ACTIVE");
        // 비밀번호는 평문으로 저장되지 않는다.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT password FROM members WHERE id = ?", String.class, memberId))
            .isNotEqualTo("Passw0rd!").startsWith("$2");

        MemberDetails customer = new MemberDetails(memberId, email, "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // ── 2. 상품 상세 조회 (비로그인도 열리는 공개 화면)
        Long productId = givenProduct(suffix, 5);
        mockMvc().perform(get("/products/{id}", productId))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/product/detail"));

        // ── 3. 장바구니에 담고 수량을 3개로 변경
        mockMvc().perform(post("/cart/items").with(user(customer)).with(csrf())
                .param("productId", String.valueOf(productId))
                .param("quantity", "2"))
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("successMessage", "장바구니에 상품을 담았습니다."));

        Long cartItemId = jdbcTemplate.queryForObject("""
            SELECT ci.id FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
             WHERE c.member_id = ? AND ci.product_id = ?
            """, Long.class, memberId, productId);

        mockMvc().perform(post("/cart/items/{id}/quantity", cartItemId)
                .with(user(customer)).with(csrf())
                .param("quantity", "3"))
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("successMessage", "수량을 변경했습니다."));

        mockMvc().perform(get("/cart").with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/cart/list"))
            .andExpect(model().attributeExists("cart"));

        // ── 4. 주문하기 → 픽업 설정 화면으로 이동 (세션에 초안 생성)
        mockMvc().perform(post("/cart/checkout").with(user(customer)).with(csrf())
                .param("itemIds", String.valueOf(cartItemId)))
            .andExpect(redirectedUrl("/orders/pickup?cartItemIds=" + cartItemId));

        MockHttpSession session = new MockHttpSession();
        mockMvc().perform(get("/orders/pickup").session(session).with(user(customer))
                .param("cartItemIds", String.valueOf(cartItemId)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/pickup-setting"))
            .andExpect(model().attributeExists("checkout", "slots", "minimumDate", "maximumDate"));

        CheckoutDraft draft =
            (CheckoutDraft) session.getAttribute(CheckoutDraft.SESSION_ATTRIBUTE);
        assertThat(draft).isNotNull();
        assertThat(draft.getMemberId()).isEqualTo(memberId);
        assertThat(draft.getCartItemIds()).containsExactly(cartItemId);

        // ── 5. 픽업 일시 선택 → 주문서
        LocalDateTime pickupAt = firstAvailableSlot();
        mockMvc().perform(post("/orders/pickup").session(session).with(user(customer)).with(csrf())
                .param("pickupAt", pickupAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .andExpect(redirectedUrl("/orders/checkout"));
        assertThat(draft.getPickupAt()).isEqualTo(pickupAt);

        mockMvc().perform(get("/orders/checkout").session(session).with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/form"))
            .andExpect(model().attributeExists("checkout", "orderForm"));

        // ── 6. 주문자·픽업자 입력 → 결제 화면
        mockMvc().perform(post("/orders/checkout").session(session).with(user(customer)).with(csrf())
                .param("ordererName", "주문자")
                .param("ordererPhone", "010-1111-2222")
                .param("pickupName", "픽업자")
                .param("pickupPhone", "010-3333-4444")
                .param("requestMessage", "E2E 요청사항"))
            .andExpect(redirectedUrl("/orders/payment"));
        assertThat(draft.isOrdererComplete()).isTrue();

        mockMvc().perform(get("/orders/payment").session(session).with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/payment/form"))
            .andExpect(model().attributeExists("checkout", "paymentForm"));

        // ── 7. 결제 → 완료 화면
        String redirect = mockMvc()
            .perform(post("/orders/payment").session(session).with(user(customer)).with(csrf())
                .param("checkoutId", draft.getCheckoutId())
                .param("method", "CARD")
                .param("refundPolicyAgreed", "true"))
            .andExpect(redirectedUrlPattern("/orders/complete?orderId=*"))
            .andReturn().getResponse().getRedirectedUrl();
        Long orderId = Long.valueOf(redirect.substring(redirect.indexOf('=') + 1));

        // 결제가 끝나면 초안은 세션에서 사라진다 — 새로고침으로 중복 결제되지 않는다.
        assertThat(session.getAttribute(CheckoutDraft.SESSION_ATTRIBUTE)).isNull();

        mockMvc().perform(get("/orders/complete").with(user(customer)).param("orderId", orderId.toString()))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/complete"))
            .andExpect(model().attributeExists("order"));

        // ── 8. DB 최종 상태: 주문 PAID, 결제 금액, 재고 차감, 장바구니 비움
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT amount FROM payments WHERE order_id = ?", Long.class, orderId))
            .isEqualTo(3 * 41_000L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId))
            .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cart_items WHERE id = ?", Integer.class, cartItemId)).isZero();

        // ── 9. 주문 상세 → 취소 → 전액 환불·재고 복구
        mockMvc().perform(get("/orders/{id}", orderId).with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/order/detail"))
            .andExpect(model().attributeExists("order", "cancelForm"));

        mockMvc().perform(post("/orders/{id}/cancel", orderId).with(user(customer)).with(csrf())
                .param("reason", "E2E 테스트 취소"))
            .andExpect(redirectedUrl("/orders/" + orderId))
            .andExpect(flash().attribute("successMessage", "주문이 취소되고 전액 환불되었습니다."));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE order_id = ?", String.class, orderId))
            .isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId))
            .isEqualTo(5);
    }

    /**
     * 개인정보·소유권이 걸린 화면은 목업 preview 가 켜진 local 프로필에서도 로그인을 요구한다.
     * preview 를 끈 운영 설정의 잠금 범위는 {@code CustomerAuthGateE2ETests} 가 검증한다.
     */
    @Test
    void 비로그인_고객은_장바구니와_마이페이지에서_로그인으로_보내진다() throws Exception {
        for (String path : List.of("/cart", "/mypage", "/reviews", "/notifications", "/mypage/coupons")) {
            mockMvc().perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        }
    }

    /** 결제 초안 없이 결제를 시도하면 주문이 만들어지지 않는다. */
    @Test
    void 초안_없이_결제_화면에_들어가면_주문이_생기지_않는다() throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, "user@cakeshop.local");
        MemberDetails customer = new MemberDetails(memberId, "user@cakeshop.local", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);

        mockMvc().perform(get("/orders/payment").with(user(customer)))
            .andExpect(status().is4xxClientError());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class))
            .isEqualTo(before);
    }

    private Long givenProduct(String suffix, int stock) {
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        String name = "E2E상품-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO products
                (category_id, name, base_price, product_type, preparation_days,
                 cancellation_limit_days, stock_quantity, status)
            VALUES (?, ?, 41000, 'GENERAL', 0, 0, ?, 'ACTIVE')
            """, categoryId, name, stock);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = ?", Long.class, name);
    }

    private LocalDateTime firstAvailableSlot() {
        LocalDate today = LocalDate.now();
        for (int day = 0; day <= 14; day++) {
            List<LocalDateTime> slots = storeService.getAvailablePickupSlots(today.plusDays(day), 0);
            if (!slots.isEmpty()) {
                return slots.getFirst();
            }
        }
        throw new AssertionError("14일 이내에 테스트 가능한 픽업 슬롯이 없습니다.");
    }
}
