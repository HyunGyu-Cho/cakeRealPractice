package com.cakeshop.domain.order.service;

import com.cakeshop.domain.cart.dto.view.CheckoutCartItem;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.order.dto.session.CheckoutDraft;
import com.cakeshop.domain.order.dto.view.CheckoutItemView;
import com.cakeshop.domain.order.dto.view.CheckoutView;
import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderItemView;
import com.cakeshop.domain.order.dto.view.OrderReferenceView;
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
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    @Autowired
    public OrderService(OrderMapper orderMapper, CartService cartService,
                        ProductService productService, StoreService storeService,
                        NotificationService notificationService) {
        this(orderMapper, cartService, productService, storeService, notificationService,
            Clock.systemDefaultZone());
    }

    public OrderService(OrderMapper orderMapper, CartService cartService,
                        ProductService productService, StoreService storeService,
                        NotificationService notificationService, Clock clock) {
        this.orderMapper = orderMapper;
        this.cartService = cartService;
        this.productService = productService;
        this.storeService = storeService;
        this.notificationService = notificationService;
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
        return new CheckoutView(draft.getCheckoutId(), items, total, preparationDays, draft.getPickupAt());
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
        order.setDiscountAmount(0L);
        order.setFinalAmount(checkout.totalAmount());
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
