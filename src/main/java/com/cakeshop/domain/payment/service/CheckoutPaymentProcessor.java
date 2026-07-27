package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.PaymentApproval;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인이 끝난 결제를 주문으로 확정하는 트랜잭션.
 *
 * <p>외부 승인 호출은 여기 들어오지 않는다({@link PaymentFacade}가 트랜잭션 밖에서 한다).
 * 이 트랜잭션이 실패하면 승인만 성사된 상태가 되므로 호출측이 보상 취소를 책임진다.
 */
@Service
public class CheckoutPaymentProcessor {
    static final Set<String> METHODS = Set.of("CARD", "KAKAO", "NAVER", "TOSS", "BANK");

    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final PaymentMapper paymentMapper;
    private final NotificationService notificationService;
    private final CouponService couponService;

    public CheckoutPaymentProcessor(OrderService orderService, CartService cartService,
                                    ProductService productService, PaymentMapper paymentMapper,
                                    NotificationService notificationService,
                                    CouponService couponService) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.productService = productService;
        this.paymentMapper = paymentMapper;
        this.notificationService = notificationService;
        this.couponService = couponService;
    }

    @Transactional
    public Long confirm(Long memberId, CheckoutDraft draft, Payment ready,
                        PaymentApproval approval, String requestedMethod) {
        orderService.validateReadyForPayment(memberId, draft);
        CheckoutView checkout = orderService.getCheckoutView(memberId, draft);
        String method = normalizeMethod(requestedMethod);

        // 준비 시점과 승인 시점 사이에 가격·쿠폰이 바뀌었을 수 있다. 승인된 금액과 다르면 확정하지 않는다.
        if (checkout.finalAmount() != approval.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        checkout.items().forEach(
            item -> productService.decreaseStock(item.productId(), item.quantity()));
        // 주문번호는 준비 단계에서 이미 제공자에게 등록한 값을 그대로 쓴다.
        Order order = orderService.createPaidOrder(draft, checkout, ready.getTossOrderId());
        // 쿠폰 사용은 결제 트랜잭션 안에서 확정한다. 조건부 UPDATE가 실패하면 예외가 올라와
        // 재고 차감·주문 생성까지 전부 롤백된다(스펙 docs/specs/coupon.md 6장 규칙 5).
        couponService.use(memberId, checkout.selectedMemberCouponId(), order.getId(),
            checkout.totalAmount());

        Payment confirmed = new Payment();
        confirmed.setId(ready.getId());
        confirmed.setOrderId(order.getId());
        confirmed.setPaymentKey(approval.paymentKey());
        confirmed.setMethod(method);
        confirmed.setAmount(approval.amount());
        confirmed.setProviderStatus(approval.providerStatus());
        confirmed.setApprovedAt(approval.approvedAt());
        if (paymentMapper.confirmPayment(confirmed) != 1) {
            // 조건의 status='READY'가 걸렀다 — 다른 요청이 먼저 확정했다는 뜻이다.
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

    static String normalizeMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(normalized)) {
            throw new BusinessException(PaymentErrorCode.INVALID_METHOD);
        }
        return normalized;
    }
}
