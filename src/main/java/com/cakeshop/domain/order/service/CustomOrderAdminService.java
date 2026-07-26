package com.cakeshop.domain.order.service;

import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.form.QuoteForm;
import com.cakeshop.domain.order.dto.form.RejectForm;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.dto.view.CustomOrderListView;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.QuoteStatus;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.mapper.CustomOrderMapper;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문제작 관리자 흐름 — 목록·상세, 견적 발송(재견적 포함), 반려.
 *
 * <p>재견적은 이전 회차를 수정하지 않고 {@code SUPERSEDED}로 내린 뒤 새 version 행을 넣어
 * 금액·제작 가능일 스냅샷을 보존한다. 밀려난 견적의 결제 링크는 같은 트랜잭션에서 회수한다.
 */
@Service
public class CustomOrderAdminService {

    /**
     * 매장 픽업 예약 가능 기간(StoreService의 픽업 창과 같은 값).
     * ⚠️ 주문제작은 제작 기간이 이보다 길 수 있어 장기 리드타임을 막는다.
     * 픽업 창을 매장 설정값으로 빼는 것은 store 공통 코드 변경이라 별도 PR 합의가 필요하다.
     */
    private static final int PICKUP_WINDOW_DAYS = 14;

    private final OrderMapper orderMapper;
    private final CustomOrderMapper customOrderMapper;
    private final CustomOrderService customOrderService;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final ChatService chatService;
    private final Clock clock;

    @Autowired
    public CustomOrderAdminService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                                   CustomOrderService customOrderService, MemberService memberService,
                                   NotificationService notificationService, ChatService chatService) {
        this(orderMapper, customOrderMapper, customOrderService, memberService,
            notificationService, chatService, Clock.systemDefaultZone());
    }

    public CustomOrderAdminService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                                   CustomOrderService customOrderService, MemberService memberService,
                                   NotificationService notificationService, ChatService chatService,
                                   Clock clock) {
        this.orderMapper = orderMapper;
        this.customOrderMapper = customOrderMapper;
        this.customOrderService = customOrderService;
        this.memberService = memberService;
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<CustomOrderListView> getRequestPage(String status, PageRequest pageRequest) {
        String normalized = normalizeStatus(status);
        long total = customOrderMapper.countCustomOrders(normalized);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<Long> orderIds = customOrderMapper.findCustomOrderIdPage(
            normalized, pageRequest.getSize(), pageRequest.getOffset());
        if (orderIds.isEmpty()) {
            return new PageResult<>(List.of(), pageRequest, total);
        }

        List<Order> orders = orderMapper.findByIds(orderIds);
        Map<Long, Order> orderById = orders.stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, List<OrderItem>> itemsByOrder = orderMapper.findItemsByOrderIds(orderIds)
            .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, CustomOrderQuote> latestQuotes = customOrderMapper
            .findLatestQuotesByOrderIds(orderIds).stream()
            .collect(Collectors.toMap(CustomOrderQuote::getOrderId, Function.identity()));
        // 회원 표시는 member 공개 계약으로만 읽는다(members 테이블 JOIN 금지).
        Map<Long, String> nicknames = memberService.getNicknameMap(
            orders.stream().map(Order::getMemberId).distinct().toList());

        LocalDateTime now = LocalDateTime.now(clock);
        List<CustomOrderListView> content = orderIds.stream()
            .map(orderById::get)
            .filter(order -> order != null)
            .map(order -> {
                List<OrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of());
                CustomOrderQuote latest = latestQuotes.get(order.getId());
                OrderStatus orderStatus = OrderStatus.valueOf(order.getStatus());
                return new CustomOrderListView(
                    order.getId(), order.getOrderNumber(), order.getMemberId(),
                    nicknames.get(order.getMemberId()),
                    items.isEmpty() ? null : items.getFirst().getProductName(),
                    order.getPickupAt(), order.getDesiredBudget(),
                    latest == null ? null : latest.getQuotedAmount(),
                    orderStatus.name(), CustomOrderService.statusLabel(orderStatus),
                    CustomOrderService.progressLabel(orderStatus, latest, null, now),
                    order.getCreatedAt());
            })
            .toList();
        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public CustomOrderDetailView getRequestDetail(Long orderId) {
        return customOrderService.getRequest(orderId);
    }

    /**
     * 견적을 발송한다. 이미 견적이 있으면 재견적(다음 version)이며,
     * 남아 있는 {@code SENT} 회차와 그 결제 링크는 같은 트랜잭션에서 무효화한다.
     */
    @Transactional
    public void quote(Long orderId, Long adminId, QuoteForm form) {
        Order order = requireUnderReview(orderId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        if (!form.getProducibleDate().isAfter(today)) {
            throw new BusinessException(CustomOrderErrorCode.PRODUCIBLE_DATE_PASSED);
        }
        // 매장 픽업 예약 창(오늘 + PICKUP_WINDOW_DAYS)을 넘는 날짜를 제시하면 고객이 견적을 수락해도
        // 잡을 수 있는 픽업 슬롯이 없다. 수락 시점이 아니라 견적 단계에서 막는다.
        if (form.getProducibleDate().isAfter(today.plusDays(PICKUP_WINDOW_DAYS))) {
            throw new BusinessException(CustomOrderErrorCode.PRODUCIBLE_DATE_OUT_OF_WINDOW);
        }

        CustomOrderQuote latest = customOrderMapper.findLatestQuoteForUpdate(orderId).orElse(null);
        if (latest != null && QuoteStatus.ACCEPTED.name().equals(latest.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.QUOTE_ALREADY_ACCEPTED);
        }
        customOrderMapper.supersedeSentQuotes(orderId);
        customOrderMapper.revokeIssuedLinks(orderId, now);

        CustomOrderQuote quote = new CustomOrderQuote();
        quote.setOrderId(orderId);
        quote.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        quote.setQuotedAmount(form.getQuotedAmount());
        quote.setProducibleDate(form.getProducibleDate());
        quote.setAdminNote(form.getAdminNote());
        quote.setIssuedBy(adminId);
        quote.setStatus(QuoteStatus.SENT.name());
        quote.setSentAt(now);
        customOrderMapper.insertQuote(quote);

        String targetUrl = "/orders/custom/" + orderId;
        notificationService.notify(new NotificationCommand(
            order.getMemberId(), NotificationType.CUSTOM_ORDER_QUOTE,
            NotificationType.CUSTOM_ORDER_QUOTE.label(),
            "주문제작 " + order.getOrderNumber() + " 견적이 도착했습니다.",
            targetUrl, orderId, null));
        chatService.postSystemCard(order.getMemberId(),
            "주문제작 견적이 도착했습니다. 확인 후 수락해 주세요.",
            "CUSTOM_ORDER_QUOTE", targetUrl);
    }

    /** 반려는 최종 상태다. 고객은 새 요청서를 쓴다(수정 재제출 없음). */
    @Transactional
    public void reject(Long orderId, RejectForm form) {
        Order order = requireUnderReview(orderId);
        if (customOrderMapper.reject(orderId, OrderStatus.UNDER_REVIEW.name(), form.getReason()) != 1) {
            throw new BusinessException(CustomOrderErrorCode.NOT_UNDER_REVIEW);
        }
        customOrderMapper.supersedeSentQuotes(orderId);
        customOrderMapper.revokeIssuedLinks(orderId, LocalDateTime.now(clock));

        notificationService.notify(NotificationCommand.forOrder(
            order.getMemberId(), NotificationType.ORDER_REJECTED, orderId,
            NotificationType.ORDER_REJECTED.label(),
            "주문제작 " + order.getOrderNumber() + " 요청이 반려되었습니다."));
    }

    private Order requireUnderReview(Long orderId) {
        Order order = orderMapper.findById(orderId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
        if (!OrderStatus.UNDER_REVIEW.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.NOT_UNDER_REVIEW);
        }
        return order;
    }

    /** 알 수 없는 상태 필터는 무시한다(목록이 비어 보이는 것보다 전체를 보여주는 편이 낫다). */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
