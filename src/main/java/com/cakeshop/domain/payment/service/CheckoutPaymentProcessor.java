package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutPaymentProcessor {
    private static final Set<String> METHODS =
        Set.of("CARD", "KAKAO", "NAVER", "TOSS", "BANK");

    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    @Autowired
    public CheckoutPaymentProcessor(OrderService orderService, CartService cartService,
                                    ProductService productService, PaymentMapper paymentMapper) {
        this(orderService, cartService, productService, paymentMapper, Clock.systemDefaultZone());
    }

    public CheckoutPaymentProcessor(OrderService orderService, CartService cartService,
                                    ProductService productService, PaymentMapper paymentMapper,
                                    Clock clock) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.productService = productService;
        this.paymentMapper = paymentMapper;
        this.clock = clock;
    }

    @Transactional
    public Long process(Long memberId, CheckoutDraft draft, String requestedMethod) {
        orderService.validateReadyForPayment(memberId, draft);
        CheckoutView checkout = orderService.getCheckoutView(memberId, draft);
        String method = normalizeMethod(requestedMethod);

        checkout.items().forEach(
            item -> productService.decreaseStock(item.productId(), item.quantity()));
        Order order = orderService.createPaidOrder(draft, checkout);

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setTossOrderId(order.getOrderNumber());
        payment.setPaymentKey("MOCK-" + UUID.randomUUID());
        payment.setIdempotencyKey(draft.getCheckoutId());
        payment.setMethod(method);
        payment.setAmount(checkout.totalAmount());
        payment.setStatus(PaymentStatus.DONE.name());
        payment.setProviderStatus("MOCK_DONE");
        payment.setApprovedAt(LocalDateTime.now(clock));
        if (paymentMapper.insertPayment(payment) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }

        cartService.removeCheckoutItems(memberId, draft.getCartItemIds());
        return order.getId();
    }

    private String normalizeMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(normalized)) {
            throw new BusinessException(PaymentErrorCode.INVALID_METHOD);
        }
        return normalized;
    }
}
