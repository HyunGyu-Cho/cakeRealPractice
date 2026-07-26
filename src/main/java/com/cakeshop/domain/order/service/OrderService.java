package com.cakeshop.domain.order.service;

import com.cakeshop.domain.cart.dto.view.CheckoutCartItem;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutItemView;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderItemView;
import com.cakeshop.domain.order.dto.view.OrderReferenceView;
import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.common.stats.MemberCountRow;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderMapper orderMapper;
    private final CartService cartService;
    private final ProductService productService;
    private final StoreService storeService;
    private final NotificationService notificationService;
    private final CouponService couponService;
    private final Clock clock;

    @Autowired
    public OrderService(OrderMapper orderMapper, CartService cartService,
                        ProductService productService, StoreService storeService,
                        NotificationService notificationService, CouponService couponService) {
        this(orderMapper, cartService, productService, storeService, notificationService,
            couponService, Clock.systemDefaultZone());
    }

    public OrderService(OrderMapper orderMapper, CartService cartService,
                        ProductService productService, StoreService storeService,
                        NotificationService notificationService, CouponService couponService,
                        Clock clock) {
        this.orderMapper = orderMapper;
        this.cartService = cartService;
        this.productService = productService;
        this.storeService = storeService;
        this.notificationService = notificationService;
        this.couponService = couponService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CheckoutDraft startCheckout(Long memberId, List<Long> cartItemIds) {
        List<CheckoutCartItem> items = cartService.getCheckoutItems(memberId, cartItemIds);
        return new CheckoutDraft(memberId, items.stream().map(CheckoutCartItem::cartItemId).toList());
    }

    @Transactional(readOnly = true)
    public CheckoutView getCheckoutView(Long memberId, CheckoutDraft draft) {
        validateDraft(memberId, draft);
        List<CheckoutCartItem> cartItems =
            cartService.getCheckoutItems(memberId, draft.getCartItemIds());
        List<CheckoutItemView> items = cartItems.stream().map(item -> {
            ProductDetailView product = productService.getProductDetail(item.productId());
            long total = Math.multiplyExact(item.currentUnitPrice(), item.quantity());
            return new CheckoutItemView(
                item.cartItemId(), item.productId(), product.name(), product.productType(),
                product.imageUrl(), item.quantity(), item.currentUnitPrice(), total,
                valueOrZero(product.preparationDays()), valueOrZero(product.cancellationLimitDays()));
        }).toList();
        long total = items.stream().mapToLong(CheckoutItemView::totalPrice).sum();
        int preparationDays = items.stream()
            .mapToInt(CheckoutItemView::preparationDays).max().orElse(0);

        List<AvailableCouponView> coupons = couponService.getApplicableCoupons(memberId, total);
        // 고른 쿠폰이 그 사이 만료·소진·사용됐으면 목록에서 빠진다. 결제를 막는 대신 선택을 지우고
        // 정가로 진행한다 — 화면이 계산한 금액을 믿지 않으므로 잘못된 금액이 결제될 일은 없다.
        Long requested = draft.getMemberCouponId();
        AvailableCouponView selected = requested == null ? null : coupons.stream()
            .filter(coupon -> coupon.memberCouponId().equals(requested))
            .findFirst().orElse(null);
        if (requested != null && selected == null) {
            draft.setMemberCouponId(null);
        }
        long discount = selected == null ? 0L : selected.discountAmount();
        return new CheckoutView(draft.getCheckoutId(), items, total, preparationDays,
            draft.getPickupAt(), coupons,
            selected == null ? null : selected.memberCouponId(), discount, total - discount);
    }

    @Transactional(readOnly = true)
    public List<LocalDateTime> getPickupSlots(Long memberId, CheckoutDraft draft, LocalDate date) {
        CheckoutView checkout = getCheckoutView(memberId, draft);
        return storeService.getAvailablePickupSlots(date, checkout.maximumPreparationDays());
    }

    @Transactional(readOnly = true)
    public void selectPickup(Long memberId, CheckoutDraft draft, LocalDateTime pickupAt) {
        CheckoutView checkout = getCheckoutView(memberId, draft);
        storeService.validatePickupAt(pickupAt, checkout.maximumPreparationDays());
        draft.setPickupAt(pickupAt);
    }

    @Transactional(readOnly = true)
    public void validateReadyForPayment(Long memberId, CheckoutDraft draft) {
        CheckoutView checkout = getCheckoutView(memberId, draft);
        if (!draft.isOrdererComplete() || draft.getPickupAt() == null) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }
        storeService.validatePickupAt(draft.getPickupAt(), checkout.maximumPreparationDays());
    }

    @Transactional
    public Order createPaidOrder(CheckoutDraft draft, CheckoutView checkout) {
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setMemberId(draft.getMemberId());
        order.setOrdererName(draft.getOrdererName());
        order.setOrdererPhone(draft.getOrdererPhone());
        order.setPickupName(draft.getPickupName());
        order.setPickupPhone(draft.getPickupPhone());
        order.setOriginalAmount(checkout.totalAmount());
        // 할인액은 CheckoutView가 들고 온 서버 계산값이다. 화면이 보낸 금액은 쓰지 않는다.
        order.setDiscountAmount(checkout.discountAmount());
        order.setFinalAmount(checkout.finalAmount());
        order.setStatus(OrderStatus.PAID.name());
        order.setPickupAt(draft.getPickupAt());
        order.setRequestMessage(draft.getRequestMessage());
        if (orderMapper.insertOrder(order) != 1 || order.getId() == null) {
            throw new BusinessException(OrderErrorCode.CREATE_FAILED);
        }

        for (CheckoutItemView checkoutItem : checkout.items()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(checkoutItem.productId());
            item.setProductName(checkoutItem.productName());
            item.setProductType(checkoutItem.productType());
            item.setQuantity(checkoutItem.quantity());
            item.setBasePrice(checkoutItem.unitPrice());
            item.setOptionAmount(0L);
            item.setTotalAmount(checkoutItem.totalPrice());
            item.setPreparationDays(checkoutItem.preparationDays());
            item.setCancellationLimitDays(checkoutItem.cancellationLimitDays());
            if (orderMapper.insertOrderItem(item) != 1) {
                throw new BusinessException(OrderErrorCode.CREATE_FAILED);
            }
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderDetailView getOwnedOrder(Long memberId, Long orderId) {
        Order order = orderMapper.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.NOT_FOUND));
        return toDetail(order);
    }

    @Transactional(readOnly = true)
    public OrderDetailView getOrder(Long orderId) {
        return toDetail(findOrder(orderId));
    }

    /**
     * [공개 계약] 후기를 쓸 수 있는 주문 항목 — 본인 주문 중 {@code PICKED_UP}인 것만.
     * review 도메인이 orders·order_items를 직접 읽지 않도록 order가 내보내는 창구다.
     * 이미 후기를 쓴 항목을 거르는 일은 자기 테이블을 아는 review가 한다.
     */
    @Transactional(readOnly = true)
    public List<ReviewableItemView> getReviewableItems(Long memberId) {
        return List.copyOf(orderMapper.findPickedUpItemsByMemberId(memberId));
    }

    /** [공개 계약] 단건 자격 검증용. 남의 주문 항목이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<ReviewableItemView> findReviewableItem(Long memberId, Long orderItemId) {
        return orderMapper.findPickedUpItem(memberId, orderItemId);
    }

    @Transactional(readOnly = true)
    public OrderReferenceView getOrderReference(Long orderId) {
        Order order = findOrder(orderId);
        return new OrderReferenceView(order.getId(), order.getOrderNumber(), order.getMemberId());
    }

    @Transactional(readOnly = true)
    public Map<Long, OrderReferenceView> getOrderReferenceMap(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.findByIds(orderIds).stream().collect(Collectors.toMap(
            Order::getId,
            order -> new OrderReferenceView(order.getId(), order.getOrderNumber(), order.getMemberId())));
    }

    /**
     * 회원별 주문 건수 배치 조회 공개 계약 — member 관리자 목록·상세가 사용한다.
     * 상대 도메인이 orders를 JOIN하지 않도록 집계는 여기서 끝낸다. 취소·반려는 세지 않는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getOrderCountMap(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.countOrdersByMemberIds(memberIds).stream()
            .collect(Collectors.toMap(MemberCountRow::memberId, MemberCountRow::count));
    }

    @Transactional
    public Order lockOrder(Long orderId) {
        return orderMapper.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItems(Long orderId) {
        return List.copyOf(orderMapper.findItemsByOrderId(orderId));
    }

    @Transactional
    public void markCanceled(Order order, String reason, String canceledBy) {
        if (orderMapper.cancelOrder(order.getId(), order.getStatus(), reason, canceledBy) != 1) {
            throw new BusinessException(OrderErrorCode.CANCELLATION_NOT_ALLOWED);
        }
    }

    @Transactional
    public void transition(Long orderId, OrderStatus next) {
        Order order = lockOrder(orderId);
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(next)
            || orderMapper.updateStatus(orderId, current.name(), next.name()) != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        notifyStatusChanged(order, next);
    }

    /** 상태 전이를 주문 고객에게 알린다. 대응하는 알림 종류가 없는 전이는 넘어간다. */
    private void notifyStatusChanged(Order order, OrderStatus next) {
        NotificationType type = switch (next) {
            case IN_PRODUCTION -> NotificationType.ORDER_IN_PRODUCTION;
            case READY_FOR_PICKUP -> NotificationType.ORDER_READY_FOR_PICKUP;
            case PICKED_UP -> NotificationType.ORDER_PICKED_UP;
            case REJECTED -> NotificationType.ORDER_REJECTED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        notificationService.notify(NotificationCommand.forOrder(
            order.getMemberId(), type, order.getId(), type.label(),
            "주문 " + order.getOrderNumber() + " · " + type.label()));
    }

    private void validateDraft(Long memberId, CheckoutDraft draft) {
        if (draft == null) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }
        if (!draft.getMemberId().equals(memberId)) {
            throw new BusinessException(OrderErrorCode.CHECKOUT_FORBIDDEN);
        }
    }

    private Order findOrder(Long orderId) {
        return orderMapper.findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.NOT_FOUND));
    }

    private OrderDetailView toDetail(Order order) {
        List<OrderItem> items = orderMapper.findItemsByOrderId(order.getId());
        int maxCancellationDays = items.stream()
            .map(OrderItem::getCancellationLimitDays)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue).max().orElse(0);
        LocalDateTime deadline = order.getPickupAt().minusDays(maxCancellationDays);
        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        boolean cancellable = (status == OrderStatus.PAID || status == OrderStatus.READY_FOR_PICKUP)
            && LocalDateTime.now(clock).isBefore(deadline);
        return new OrderDetailView(
            order.getId(), order.getOrderNumber(), order.getMemberId(),
            order.getOrdererName(), order.getOrdererPhone(),
            order.getPickupName(), order.getPickupPhone(),
            order.getOriginalAmount(), order.getDiscountAmount(), order.getFinalAmount(),
            order.getStatus(), order.getPickupAt(), order.getRequestMessage(),
            order.getCancelReason(), order.getCanceledBy(), deadline, cancellable,
            order.getCreatedAt(), items.stream().map(this::toItemView).toList());
    }

    private OrderItemView toItemView(OrderItem item) {
        return new OrderItemView(
            item.getId(), item.getProductId(), item.getProductName(), item.getProductType(),
            item.getQuantity(), item.getBasePrice(), item.getTotalAmount(),
            valueOrZero(item.getPreparationDays()), valueOrZero(item.getCancellationLimitDays()));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDate.now(clock).format(ORDER_DATE) + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
