package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
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
    private final NotificationService notificationService;
    private final CouponService couponService;
    private final Clock clock;

    @Autowired
    public CheckoutPaymentProcessor(OrderService orderService, CartService cartService,
                                    ProductService productService, PaymentMapper paymentMapper,
                                    NotificationService notificationService,
                                    CouponService couponService) {
        this(orderService, cartService, productService, paymentMapper, notificationService,
            couponService, Clock.systemDefaultZone());
    }

    public CheckoutPaymentProcessor(OrderService orderService, CartService cartService,
                                    ProductService productService, PaymentMapper paymentMapper,
                                    NotificationService notificationService,
                                    CouponService couponService, Clock clock) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.productService = productService;
        this.paymentMapper = paymentMapper;
        this.notificationService = notificationService;
        this.couponService = couponService;
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
        // 쿠폰 사용은 결제 트랜잭션 안에서 확정한다. 조건부 UPDATE가 실패하면 예외가 올라와
        // 재고 차감·주문 생성까지 전부 롤백된다(스펙 docs/specs/coupon.md 6장 규칙 5).
        couponService.use(memberId, checkout.selectedMemberCouponId(), order.getId(),
            checkout.totalAmount());

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setTossOrderId(order.getOrderNumber());
        payment.setPaymentKey("MOCK-" + UUID.randomUUID());
        payment.setIdempotencyKey(draft.getCheckoutId());
        payment.setMethod(method);
        payment.setAmount(checkout.finalAmount());
        payment.setStatus(PaymentStatus.DONE.name());
        payment.setProviderStatus("MOCK_DONE");
        payment.setApprovedAt(LocalDateTime.now(clock));
        if (paymentMapper.insertPayment(payment) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED);
        }

        cartService.removeCheckoutItems(memberId, draft.getCartItemIds());
        notifyPaid(order);
        return order.getId();
    }

    /** 결제 성공을 고객에게, 신규 주문을 전체 관리자에게 알린다. */
    private void notifyPaid(Order order) {
        notificationService.notify(NotificationCommand.forOrder(
            order.getMemberId(), NotificationType.ORDER_PAID, order.getId(),
            NotificationType.ORDER_PAID.label(),
            "주문 " + order.getOrderNumber() + " 결제가 완료되었습니다."));
        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_ORDER_PLACED,
            NotificationType.ADMIN_ORDER_PLACED.label(),
            "새 주문 " + order.getOrderNumber() + "이 결제되었습니다.",
            "/admin/orders/" + order.getId(), order.getId(), null));
    }

    private String normalizeMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(normalized)) {
            throw new BusinessException(PaymentErrorCode.INVALID_METHOD);
        }
        return normalized;
    }
}
