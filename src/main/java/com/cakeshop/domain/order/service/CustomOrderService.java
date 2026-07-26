package com.cakeshop.domain.order.service;

import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.form.CustomOrderForm;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.dto.view.CustomOrderOptionView;
import com.cakeshop.domain.order.dto.view.CustomOrderQuoteView;
import com.cakeshop.domain.order.entity.CustomOrderPaymentLink;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.PaymentLinkStatus;
import com.cakeshop.domain.order.entity.QuoteStatus;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.CustomOrderMapper;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionView;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주문제작 고객 흐름 — 요청서 제출, 상세 조회, 견적 수락(결제 링크 발급), 취소.
 *
 * <p>요청서는 전용 테이블 없이 {@code orders}(UNDER_REVIEW) + {@code order_items} 계열로 저장한다.
 * {@link OrderStatus} 7개는 팀 확정값이라 바꾸지 않고, 견적 진행은 {@code custom_order_quotes}가 소유한다.
 */
@Service
public class CustomOrderService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String IMAGE_DIRECTORY = "custom-order";
    private static final int MAX_IMAGES = 3;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    /** 결제 링크 유효기간. 제작 가능일 전날과 비교해 이른 쪽을 쓴다. */
    private static final int LINK_VALID_HOURS = 72;
    /** 제작 가능일 이후 픽업 슬롯을 찾아볼 최대 일수(휴무일 연속을 넘기기 위한 여유). */
    private static final int PICKUP_SEARCH_DAYS = 14;

    private final OrderMapper orderMapper;
    private final CustomOrderMapper customOrderMapper;
    private final ProductService productService;
    private final StoreService storeService;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final ChatService chatService;
    private final FileStorageClient fileStorageClient;
    private final Clock clock;

    @Autowired
    public CustomOrderService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                              ProductService productService, StoreService storeService,
                              MemberService memberService, NotificationService notificationService,
                              ChatService chatService, FileStorageClient fileStorageClient) {
        this(orderMapper, customOrderMapper, productService, storeService, memberService,
            notificationService, chatService, fileStorageClient, Clock.systemDefaultZone());
    }

    public CustomOrderService(OrderMapper orderMapper, CustomOrderMapper customOrderMapper,
                              ProductService productService, StoreService storeService,
                              MemberService memberService, NotificationService notificationService,
                              ChatService chatService, FileStorageClient fileStorageClient,
                              Clock clock) {
        this.orderMapper = orderMapper;
        this.customOrderMapper = customOrderMapper;
        this.productService = productService;
        this.storeService = storeService;
        this.memberService = memberService;
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.fileStorageClient = fileStorageClient;
        this.clock = clock;
    }

    // ==================== 요청서 작성 화면 ====================

    /** 요청서 화면의 옵션 목록. 추가 금액의 정본은 DB이며 화면 계산은 표시 전용이다. */
    @Transactional(readOnly = true)
    public List<ProductOptionGroupView> getOptionGroups(Long productId) {
        requireCustomProduct(productId);
        return productService.getOptionGroups(productId);
    }

    @Transactional(readOnly = true)
    public ProductDetailView getCustomProduct(Long productId) {
        return requireCustomProduct(productId);
    }

    /** 주문제작 상품이 하나뿐인 1차 구성에서 화면 진입용 기본 상품을 고른다. */
    @Transactional(readOnly = true)
    public Optional<ProductDetailView> findDefaultCustomProduct() {
        return productService.getLatestActiveProducts(50).stream()
            .filter(product -> ProductType.CUSTOM.name().equals(product.productType()))
            .findFirst()
            .map(product -> productService.getProductDetail(product.id()));
    }

    // ==================== 요청서 제출 ====================

    /**
     * 요청서를 제출해 {@code UNDER_REVIEW} 주문을 만든다.
     * 초안을 저장하지 않으므로 이 한 트랜잭션에서 주문·항목·옵션·이미지를 모두 넣는다.
     */
    @Transactional
    public Long submitRequest(Long memberId, CustomOrderForm form) {
        ProductDetailView product = requireCustomProduct(form.getProductId());
        if (!product.onSale()) {
            throw new BusinessException(CustomOrderErrorCode.PRODUCT_NOT_ON_SALE);
        }

        List<SelectedOption> selected = resolveOptions(form.getProductId(), form.getOptionIds());
        List<MultipartFile> images = validateImages(form.getReferenceImages());

        // 요청서 단계의 픽업 하한은 상품 준비일이다. 견적 수락 시 제작 가능일로 다시 검증한다.
        int preparationDays = valueOrZero(product.preparationDays());
        storeService.validatePickupAt(form.getPickupAt(), preparationDays);

        long optionAmount = selected.stream().mapToLong(SelectedOption::additionalPrice).sum();
        long estimated = Math.addExact(product.basePrice(), optionAmount);

        // 주문자 정보는 member 도메인의 공개 계약으로만 읽는다(members 테이블 JOIN 금지).
        MemberProfileView profile = memberService.getProfile(memberId);
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setMemberId(memberId);
        order.setOrdererName(profile.nickname());
        order.setOrdererPhone(blankToDash(profile.phone()));
        order.setPickupName(profile.nickname());
        order.setPickupPhone(blankToDash(profile.phone()));
        order.setOriginalAmount(estimated);
        order.setDiscountAmount(0L);
        // 견적 전이라 확정 금액이 아니다. 견적 수락 시 견적 금액으로 덮어쓴다.
        order.setFinalAmount(estimated);
        order.setDesiredBudget(form.getDesiredBudget());
        order.setStatus(OrderStatus.UNDER_REVIEW.name());
        order.setPickupAt(form.getPickupAt());
        order.setRequestMessage(buildRequestMessage(form));
        if (orderMapper.insertOrder(order) != 1 || order.getId() == null) {
            throw new BusinessException(OrderErrorCode.CREATE_FAILED);
        }

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.id());
        item.setProductName(product.name());
        item.setProductType(ProductType.CUSTOM.name());
        item.setQuantity(1);
        item.setBasePrice(product.basePrice());
        item.setOptionAmount(optionAmount);
        item.setTotalAmount(estimated);
        item.setRequirements(form.getRequirements());
        item.setPreparationDays(preparationDays);
        item.setCancellationLimitDays(valueOrZero(product.cancellationLimitDays()));
        if (orderMapper.insertOrderItem(item) != 1 || item.getId() == null) {
            throw new BusinessException(OrderErrorCode.CREATE_FAILED);
        }

        for (SelectedOption option : selected) {
            OrderItemOption row = new OrderItemOption();
            row.setOrderItemId(item.getId());
            row.setProductOptionId(option.optionId());
            row.setOptionGroupName(option.groupName());
            row.setOptionName(option.optionName());
            row.setAdditionalPrice(option.additionalPrice());
            customOrderMapper.insertOrderItemOption(row);
        }

        int sortOrder = 0;
        for (MultipartFile image : images) {
            OrderItemImage row = new OrderItemImage();
            row.setOrderItemId(item.getId());
            row.setImageUrl(fileStorageClient.store(image, IMAGE_DIRECTORY));
            row.setSortOrder(sortOrder++);
            customOrderMapper.insertOrderItemImage(row);
        }

        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_ORDER_PLACED,
            "신규 주문제작 요청",
            "주문제작 요청 " + order.getOrderNumber() + "이 접수되었습니다.",
            "/admin/custom-orders/" + order.getId(), order.getId(), null));
        return order.getId();
    }

    // ==================== 조회 ====================

    @Transactional(readOnly = true)
    public CustomOrderDetailView getMyRequest(Long memberId, Long orderId) {
        Order order = orderMapper.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
        return toDetail(order);
    }

    /** 관리자 상세. 소유권 검사가 없다는 점만 고객 조회와 다르다. */
    @Transactional(readOnly = true)
    public CustomOrderDetailView getRequest(Long orderId) {
        return toDetail(requireCustomOrder(orderId));
    }

    /** orderId 없이 들어온 목업 URL(/orders/custom/request)의 착지점. */
    @Transactional(readOnly = true)
    public Optional<Long> findMyLatestRequestId(Long memberId) {
        return customOrderMapper.findLatestOrderIdByMemberId(memberId);
    }

    // ==================== 견적 수락 → 결제 링크 발급 ====================

    /**
     * 고객이 최신 견적을 수락하고 결제 링크를 발급받는다.
     * 견적 금액을 주문 확정 금액으로 올리고, 제작 가능일 기준으로 픽업 일시를 다시 검증한다.
     */
    @Transactional
    public String acceptQuote(Long memberId, Long orderId) {
        Order order = orderMapper.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
        requireUnderReview(order);

        CustomOrderQuote quote = customOrderMapper.findLatestQuoteForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.QUOTE_NOT_SENT));
        if (!QuoteStatus.SENT.name().equals(quote.getStatus())) {
            throw new BusinessException(QuoteStatus.ACCEPTED.name().equals(quote.getStatus())
                ? CustomOrderErrorCode.QUOTE_ALREADY_ACCEPTED
                : CustomOrderErrorCode.QUOTE_NOT_SENT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (customOrderMapper.updateQuoteStatus(quote.getId(), QuoteStatus.SENT.name(),
            QuoteStatus.ACCEPTED.name(), now) != 1) {
            throw new BusinessException(CustomOrderErrorCode.QUOTE_NOT_SENT);
        }

        // 제작 가능일이 희망 픽업일보다 늦으면 픽업을 민다. 밀린 날짜가 휴무일·영업시간 밖일 수
        // 있으므로 그대로 쓰지 않고 실제 예약 가능한 슬롯으로 스냅한다(스펙 6장 규칙 9).
        LocalDateTime pickupAt = order.getPickupAt();
        if (pickupAt.toLocalDate().isBefore(quote.getProducibleDate())) {
            pickupAt = firstSlotFrom(quote.getProducibleDate(), pickupAt.toLocalTime());
            customOrderMapper.updatePickupAt(orderId, pickupAt);
        }
        customOrderMapper.updateFinalAmount(orderId, quote.getQuotedAmount());

        CustomOrderPaymentLink link = issueLink(quote, now);
        String targetUrl = "/orders/custom/pay/" + link.getToken();
        notificationService.notify(new NotificationCommand(
            order.getMemberId(), NotificationType.PAYMENT_REQUESTED,
            NotificationType.PAYMENT_REQUESTED.label(),
            "주문제작 " + order.getOrderNumber() + " 결제를 진행해 주세요.",
            targetUrl, orderId, null));
        chatService.postSystemCard(order.getMemberId(),
            "주문제작 결제가 준비되었습니다. 링크에서 결제를 완료해 주세요.",
            "CUSTOM_ORDER_PAYMENT", targetUrl);
        return link.getToken();
    }

    // ==================== 고객 취소 ====================

    /** 견적 수락 전까지만 고객이 취소할 수 있다. 결제 후에는 일반 주문 취소 규칙을 따른다. */
    @Transactional
    public void cancelRequest(Long memberId, Long orderId, String reason) {
        Order order = orderMapper.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
        if (!OrderStatus.UNDER_REVIEW.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        CustomOrderQuote latest = customOrderMapper.findLatestQuote(orderId).orElse(null);
        if (latest != null && QuoteStatus.ACCEPTED.name().equals(latest.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        if (orderMapper.cancelOrder(orderId, OrderStatus.UNDER_REVIEW.name(),
            reason, "CUSTOMER") != 1) {
            throw new BusinessException(CustomOrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        customOrderMapper.revokeIssuedLinks(orderId, LocalDateTime.now(clock));
    }

    // ==================== 내부 헬퍼 ====================

    /**
     * 제작 가능일 이후 첫 예약 가능 슬롯. 같은 시각대를 우선하고 없으면 그날의 가장 이른 슬롯을 쓴다.
     * 매장 픽업 창(오늘+14일) 밖이면 슬롯이 없으므로, 관리자가 견적 단계에서 창 안의 날짜를 제시하도록
     * {@code CustomOrderAdminService}가 먼저 막는다. 그래도 비면 명확한 오류로 알린다.
     */
    private LocalDateTime firstSlotFrom(LocalDate from, java.time.LocalTime preferredTime) {
        for (LocalDate date = from; !date.isAfter(from.plusDays(PICKUP_SEARCH_DAYS)); date = date.plusDays(1)) {
            List<LocalDateTime> slots = storeService.getAvailablePickupSlots(date, 0);
            if (slots.isEmpty()) {
                continue;
            }
            return slots.stream()
                .filter(slot -> slot.toLocalTime().equals(preferredTime))
                .findFirst()
                .orElseGet(slots::getFirst);
        }
        throw new BusinessException(CustomOrderErrorCode.NO_PICKUP_SLOT);
    }

    /** 견적당 링크 1건(UNIQUE)이라 이미 있으면 그대로 돌려준다(수락 재시도 멱등). */
    private CustomOrderPaymentLink issueLink(CustomOrderQuote quote, LocalDateTime now) {
        CustomOrderPaymentLink existing = customOrderMapper.findLinkByQuoteId(quote.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        CustomOrderPaymentLink link = new CustomOrderPaymentLink();
        link.setQuoteId(quote.getId());
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setAmount(quote.getQuotedAmount());
        link.setExpiresAt(expiresAt(quote, now));
        link.setStatus(PaymentLinkStatus.ISSUED.name());
        customOrderMapper.insertPaymentLink(link);
        return link;
    }

    /** 발급 후 72시간과 제작 가능일 전날 자정 중 이른 쪽. */
    private LocalDateTime expiresAt(CustomOrderQuote quote, LocalDateTime now) {
        LocalDateTime byHours = now.plusHours(LINK_VALID_HOURS);
        LocalDateTime byProducibleDate = quote.getProducibleDate().minusDays(1).atStartOfDay();
        return byProducibleDate.isBefore(byHours) ? byProducibleDate : byHours;
    }

    private ProductDetailView requireCustomProduct(Long productId) {
        ProductDetailView product = productService.getProductDetail(productId);
        if (!ProductType.CUSTOM.name().equals(product.productType())) {
            throw new BusinessException(CustomOrderErrorCode.NOT_CUSTOM_PRODUCT);
        }
        return product;
    }

    private Order requireCustomOrder(Long orderId) {
        return orderMapper.findById(orderId)
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
    }

    private void requireUnderReview(Order order) {
        if (OrderStatus.IN_PRODUCTION.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.ALREADY_PAID);
        }
        if (!OrderStatus.UNDER_REVIEW.name().equals(order.getStatus())) {
            throw new BusinessException(CustomOrderErrorCode.NOT_UNDER_REVIEW);
        }
    }

    /**
     * 선택한 옵션 id를 상품의 실제 옵션과 대조해 이름·추가 금액을 서버에서 다시 읽는다.
     * 클라이언트가 보낸 금액은 계산에도 저장에도 쓰지 않는다.
     */
    private List<SelectedOption> resolveOptions(Long productId, List<Long> optionIds) {
        List<ProductOptionGroupView> groups = productService.getOptionGroups(productId);
        Set<Long> requested = optionIds == null ? Set.of() : new LinkedHashSet<>(optionIds);

        Map<Long, ProductOptionGroupView> groupByOptionId = groups.stream()
            .flatMap(group -> group.options().stream()
                .map(option -> Map.entry(option.id(), group)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (Long optionId : requested) {
            if (!groupByOptionId.containsKey(optionId)) {
                throw new BusinessException(CustomOrderErrorCode.INVALID_OPTION);
            }
        }
        Set<Long> chosenGroups = requested.stream()
            .map(optionId -> groupByOptionId.get(optionId).id())
            .collect(Collectors.toCollection(HashSet::new));
        for (ProductOptionGroupView group : groups) {
            if (group.required() && !group.options().isEmpty() && !chosenGroups.contains(group.id())) {
                throw new BusinessException(CustomOrderErrorCode.OPTION_REQUIRED);
            }
        }

        List<SelectedOption> selected = new ArrayList<>();
        for (ProductOptionGroupView group : groups) {
            for (ProductOptionView option : group.options()) {
                if (requested.contains(option.id())) {
                    selected.add(new SelectedOption(
                        option.id(), group.name(), option.name(), option.additionalPrice()));
                }
            }
        }
        return selected;
    }

    private List<MultipartFile> validateImages(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        List<MultipartFile> present = images.stream()
            .filter(image -> image != null && !image.isEmpty())
            .toList();
        if (present.size() > MAX_IMAGES) {
            throw new BusinessException(CustomOrderErrorCode.TOO_MANY_IMAGES);
        }
        for (MultipartFile image : present) {
            if (image.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException(CustomOrderErrorCode.IMAGE_TOO_LARGE);
            }
            String contentType = image.getContentType();
            if (contentType == null
                || !IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(CustomOrderErrorCode.INVALID_IMAGE);
            }
        }
        return present;
    }

    private CustomOrderDetailView toDetail(Order order) {
        Long itemId = customOrderMapper.findCustomOrderItemId(order.getId())
            .orElseThrow(() -> new BusinessException(CustomOrderErrorCode.REQUEST_NOT_FOUND));
        List<OrderItem> items = orderMapper.findItemsByOrderId(order.getId());
        OrderItem item = items.isEmpty() ? new OrderItem() : items.getFirst();

        List<CustomOrderOptionView> options = customOrderMapper.findOptionsByOrderItemId(itemId)
            .stream()
            .map(row -> new CustomOrderOptionView(
                row.getOptionGroupName(), row.getOptionName(), row.getAdditionalPrice()))
            .toList();
        List<String> imageUrls = customOrderMapper.findImagesByOrderItemId(itemId)
            .stream().map(OrderItemImage::getImageUrl).toList();

        List<CustomOrderQuote> quotes = customOrderMapper.findQuotesByOrderId(order.getId());
        List<CustomOrderQuoteView> quoteViews = quotes.stream().map(this::toQuoteView).toList();
        CustomOrderQuote latest = quotes.isEmpty() ? null : quotes.getFirst();

        CustomOrderPaymentLink link = latest == null ? null
            : customOrderMapper.findLinkByQuoteId(latest.getId()).orElse(null);

        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        boolean sentQuote = latest != null && QuoteStatus.SENT.name().equals(latest.getStatus());
        boolean acceptedQuote = latest != null && QuoteStatus.ACCEPTED.name().equals(latest.getStatus());
        LocalDateTime now = LocalDateTime.now(clock);
        boolean payable = link != null
            && PaymentLinkStatus.ISSUED.name().equals(link.getStatus())
            && now.isBefore(link.getExpiresAt())
            && status == OrderStatus.UNDER_REVIEW;

        return new CustomOrderDetailView(
            order.getId(), order.getOrderNumber(), order.getMemberId(),
            item.getProductName(),
            item.getProductId() == null ? null : productImageUrl(item.getProductId()),
            options, letteringOf(order.getRequestMessage()), item.getRequirements(), imageUrls,
            order.getPickupAt(), order.getDesiredBudget(),
            item.getTotalAmount() == null ? 0L : item.getTotalAmount(),
            acceptedQuote ? order.getFinalAmount() : null,
            status.name(), statusLabel(status), progressLabel(status, latest, link, now),
            order.getRejectReason(), quoteViews,
            quoteViews.isEmpty() ? null : quoteViews.getFirst(),
            link == null ? null : link.getToken(),
            link == null ? null : link.getExpiresAt(),
            sentQuote && status == OrderStatus.UNDER_REVIEW,
            payable,
            status == OrderStatus.UNDER_REVIEW && !acceptedQuote,
            order.getCreatedAt());
    }

    private String productImageUrl(Long productId) {
        try {
            return productService.getProductDetail(productId).imageUrl();
        } catch (BusinessException exception) {
            return null; // 상품이 지워져도 주문 상세는 스냅샷으로 그려진다
        }
    }

    private CustomOrderQuoteView toQuoteView(CustomOrderQuote quote) {
        QuoteStatus status = QuoteStatus.valueOf(quote.getStatus());
        return new CustomOrderQuoteView(
            quote.getId(), quote.getVersion(), quote.getQuotedAmount(), quote.getProducibleDate(),
            quote.getAdminNote(), status.name(), status.label(),
            quote.getSentAt(), quote.getAcceptedAt());
    }

    /**
     * 화면 라벨은 주문 상태와 견적·링크 상태를 조합한 <b>파생값</b>이다. 저장하지 않는다.
     */
    static String progressLabel(OrderStatus status, CustomOrderQuote latest,
                                CustomOrderPaymentLink link, LocalDateTime now) {
        if (status != OrderStatus.UNDER_REVIEW) {
            return statusLabel(status);
        }
        if (latest == null) {
            return "검토 대기";
        }
        if (QuoteStatus.SENT.name().equals(latest.getStatus())) {
            return "견적 발송됨";
        }
        if (QuoteStatus.ACCEPTED.name().equals(latest.getStatus())) {
            if (link != null && PaymentLinkStatus.ISSUED.name().equals(link.getStatus())
                && now.isAfter(link.getExpiresAt())) {
                return "결제 링크 만료";
            }
            return "결제 대기";
        }
        return "재견적 준비 중";
    }

    static String statusLabel(OrderStatus status) {
        return switch (status) {
            case UNDER_REVIEW -> "확인 중";
            case IN_PRODUCTION -> "제작 중";
            case READY_FOR_PICKUP -> "픽업 대기";
            case PICKED_UP -> "픽업 완료";
            case REJECTED -> "반려";
            case CANCELED -> "주문 취소";
            case PAID -> "결제 완료";
        };
    }

    /** 레터링은 별도 컬럼이 없어 요청 메모 앞머리에 규격 문자열로 넣고 화면에서 되읽는다. */
    private static final String LETTERING_PREFIX = "[레터링] ";

    private String buildRequestMessage(CustomOrderForm form) {
        if (!StringUtils.hasText(form.getLettering())) {
            return null;
        }
        return LETTERING_PREFIX + form.getLettering().trim();
    }

    private String letteringOf(String requestMessage) {
        if (requestMessage == null || !requestMessage.startsWith(LETTERING_PREFIX)) {
            return null;
        }
        return requestMessage.substring(LETTERING_PREFIX.length());
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String generateOrderNumber() {
        return "CUS-" + LocalDate.now(clock).format(ORDER_DATE) + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private record SelectedOption(Long optionId, String groupName, String optionName,
                                  long additionalPrice) {
    }
}
