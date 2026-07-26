package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.dto.form.OrderSearchForm;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.service.OrderAdminService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.dto.form.PaymentSearchForm;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.common.paging.PageRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class OrderPaymentIntegrationTests {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrderService orderService;
    @Autowired OrderAdminService orderAdminService;
    @Autowired PaymentFacade paymentFacade;
    @Autowired PaymentService paymentService;
    @Autowired RefundService refundService;
    @Autowired StoreService storeService;

    @Test
    void paidOrderDuplicatePostAndFullRefundWorkAgainstMariaDb() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "order-" + suffix + "@test.local";
        jdbcTemplate.update("""
            INSERT INTO members (email, password, nickname, phone)
            VALUES (?, ?, ?, ?)
            """, email, "{noop}test", "주문테스트-" + suffix, "010-1234-5678");
        Long memberId = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);

        String productName = "결제통합-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO products
                (category_id, name, base_price, product_type, preparation_days,
                 cancellation_limit_days, stock_quantity, status)
            VALUES (?, ?, 41000, 'GENERAL', 0, 0, 5, 'ACTIVE')
            """, categoryId, productName);
        Long productId = jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = ?", Long.class, productName);
        jdbcTemplate.update("INSERT INTO carts (member_id) VALUES (?)", memberId);
        Long cartId = jdbcTemplate.queryForObject(
            "SELECT id FROM carts WHERE member_id = ?", Long.class, memberId);
        jdbcTemplate.update(
            "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 2)",
            cartId, productId);
        Long cartItemId = jdbcTemplate.queryForObject(
            "SELECT id FROM cart_items WHERE cart_id = ?", Long.class, cartId);

        CheckoutDraft draft = orderService.startCheckout(memberId, List.of(cartItemId));
        draft.setPickupAt(firstAvailableSlot());
        GeneralOrderForm orderForm = new GeneralOrderForm();
        orderForm.setOrdererName("주문자");
        orderForm.setOrdererPhone("010-1111-2222");
        orderForm.setPickupName("픽업자");
        orderForm.setPickupPhone("010-3333-4444");
        orderForm.setRequestMessage("테스트 요청");
        draft.updateOrderer(orderForm);

        Long orderId = paymentFacade.pay(memberId, draft.getCheckoutId(), draft, "CARD");
        Long duplicateOrderId =
            paymentFacade.pay(memberId, draft.getCheckoutId(), draft, "CARD");

        assertThat(duplicateOrderId).isEqualTo(orderId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT amount FROM payments WHERE order_id = ?", Long.class, orderId))
            .isEqualTo(82_000L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId))
            .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cart_items WHERE id = ?", Integer.class, cartItemId))
            .isZero();
        assertThat(orderAdminService.getOrderPage(
            new OrderSearchForm(), new PageRequest(1, 20)).getContent())
            .anyMatch(order -> order.id().equals(orderId));
        assertThat(paymentService.getPaymentPage(
            new PaymentSearchForm(), new PageRequest(1, 20)).getContent())
            .anyMatch(payment -> payment.orderId().equals(orderId));

        refundService.cancelByCustomer(memberId, orderId, "통합 테스트 취소");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE order_id = ?", String.class, orderId))
            .isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM payment_cancellations
             WHERE payment_id = (SELECT id FROM payments WHERE order_id = ?)
            """, String.class, orderId)).isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId))
            .isEqualTo(5);
        PaymentSearchForm canceled = new PaymentSearchForm();
        canceled.setStatus("CANCELED");
        assertThat(paymentService.getPaymentPage(canceled, new PageRequest(1, 20)).getContent())
            .anyMatch(payment -> payment.orderId().equals(orderId)
                && "DONE".equals(payment.cancellationStatus()));
    }

    private LocalDateTime firstAvailableSlot() {
        LocalDate today = LocalDate.now();
        for (int day = 0; day <= 14; day++) {
            List<LocalDateTime> slots =
                storeService.getAvailablePickupSlots(today.plusDays(day), 0);
            if (!slots.isEmpty()) {
                return slots.getFirst();
            }
        }
        throw new AssertionError("14일 이내에 테스트 가능한 픽업 슬롯이 없습니다.");
    }
}
