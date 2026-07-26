package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.form.CustomOrderForm;
import com.cakeshop.domain.order.entity.CustomOrderPaymentLink;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.PaymentLinkStatus;
import com.cakeshop.domain.order.entity.QuoteStatus;
import com.cakeshop.domain.order.error.CustomOrderErrorCode;
import com.cakeshop.domain.order.mapper.CustomOrderMapper;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOrderServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Long MEMBER_ID = 7L;
    private static final Long PRODUCT_ID = 3L;

    @Mock private OrderMapper orderMapper;
    @Mock private CustomOrderMapper customOrderMapper;
    @Mock private ProductService productService;
    @Mock private StoreService storeService;
    @Mock private MemberService memberService;
    @Mock private NotificationService notificationService;
    @Mock private ChatService chatService;
    @Mock private FileStorageClient fileStorageClient;

    private CustomOrderService customOrderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        customOrderService = new CustomOrderService(orderMapper, customOrderMapper, productService,
            storeService, memberService, notificationService, chatService, fileStorageClient, clock);

        when(productService.getProductDetail(PRODUCT_ID)).thenReturn(customProduct(true));
        when(productService.getOptionGroups(PRODUCT_ID)).thenReturn(optionGroups());
        when(memberService.getProfile(MEMBER_ID))
            .thenReturn(new MemberProfileView(MEMBER_ID, "지수", "a@b.c", "010-0000-0000", NOW));
        when(orderMapper.insertOrder(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Order.class).setId(100L);
            return 1;
        });
        when(orderMapper.insertOrderItem(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OrderItem.class).setId(200L);
            return 1;
        });
        // 픽업 슬롯 스냅용 — 그날 10:00·14:00 두 슬롯이 열려 있다고 본다.
        // any() 는 null 도 매칭하므로 날짜가 없으면 빈 목록으로 돌려준다.
        when(storeService.getAvailablePickupSlots(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation -> {
                LocalDate date = invocation.getArgument(0, LocalDate.class);
                return date == null ? List.of() : List.of(date.atTime(10, 0), date.atTime(14, 0));
            });
    }

    // ==================== 요청서 제출 ====================

    @Test
    void submitCreatesUnderReviewOrderWithServerSideOptionAmount() {
        CustomOrderForm form = form(List.of(11L, 21L));

        Long orderId = customOrderService.submitRequest(MEMBER_ID, form);

        assertThat(orderId).isEqualTo(100L);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insertOrder(captor.capture());
        Order saved = captor.getValue();
        // 수제 주문의 시작 상태는 UNDER_REVIEW다(확정 enum 그대로).
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.UNDER_REVIEW.name());
        // 55000(기본) + 5000(2호) + 2000(초코) — 클라이언트가 아니라 DB 값으로 계산한다.
        assertThat(saved.getOriginalAmount()).isEqualTo(62_000L);
        assertThat(saved.getFinalAmount()).isEqualTo(62_000L);
        assertThat(saved.getDesiredBudget()).isEqualTo(150_000L);
        verify(customOrderMapper, org.mockito.Mockito.times(2)).insertOrderItemOption(any());
    }

    @Test
    void submitNotifiesAdminsOnly() {
        customOrderService.submitRequest(MEMBER_ID, form(List.of(11L, 21L)));

        verify(notificationService).notifyAdmins(any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    void submitRejectsUnknownOption() {
        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form(List.of(999L))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.INVALID_OPTION);
        verify(orderMapper, never()).insertOrder(any());
    }

    @Test
    void submitRejectsMissingRequiredOptionGroup() {
        // 맛(21L)만 고르고 필수인 크기 그룹을 비웠다
        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form(List.of(21L))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.OPTION_REQUIRED);
    }

    @Test
    void submitRejectsNonCustomProduct() {
        when(productService.getProductDetail(PRODUCT_ID)).thenReturn(generalProduct());

        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form(List.of())))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.NOT_CUSTOM_PRODUCT);
    }

    @Test
    void submitRejectsProductNotOnSale() {
        when(productService.getProductDetail(PRODUCT_ID)).thenReturn(customProduct(false));

        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form(List.of(11L, 21L))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void submitRejectsFourthReferenceImage() {
        CustomOrderForm form = form(List.of(11L, 21L));
        form.setReferenceImages(List.of(jpg(), jpg(), jpg(), jpg()));

        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.TOO_MANY_IMAGES);
    }

    @Test
    void submitRejectsNonImageAttachment() {
        CustomOrderForm form = form(List.of(11L, 21L));
        form.setReferenceImages(List.of(
            new MockMultipartFile("referenceImages", "a.pdf", "application/pdf", new byte[] {1})));

        assertThatThrownBy(() -> customOrderService.submitRequest(MEMBER_ID, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.INVALID_IMAGE);
    }

    // ==================== 견적 수락 ====================

    @Test
    void acceptQuoteIssuesLinkAndFixesFinalAmount() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        CustomOrderQuote quote = quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 10));
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.of(quote));
        when(customOrderMapper.updateQuoteStatus(eq(5L), eq("SENT"), eq("ACCEPTED"), any())).thenReturn(1);
        when(customOrderMapper.findLinkByQuoteId(5L)).thenReturn(Optional.empty());

        String token = customOrderService.acceptQuote(MEMBER_ID, 100L);

        assertThat(token).isNotBlank();
        verify(customOrderMapper).updateFinalAmount(100L, 180_000L);
        ArgumentCaptor<CustomOrderPaymentLink> captor =
            ArgumentCaptor.forClass(CustomOrderPaymentLink.class);
        verify(customOrderMapper).insertPaymentLink(captor.capture());
        CustomOrderPaymentLink link = captor.getValue();
        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.ISSUED.name());
        assertThat(link.getAmount()).isEqualTo(180_000L);
        // 72시간(8/4 10:00)과 제작 가능일 전날(8/9 00:00) 중 이른 쪽
        assertThat(link.getExpiresAt()).isEqualTo(NOW.plusHours(72));
        verify(chatService).postSystemCard(eq(MEMBER_ID), anyString(),
            eq("CUSTOM_ORDER_PAYMENT"), anyString());
    }

    @Test
    void acceptQuoteCapsExpiryAtDayBeforeProducibleDate() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        // 제작 가능일이 이틀 뒤 → 전날 00:00이 72시간보다 이르다
        CustomOrderQuote quote = quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 3));
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.of(quote));
        when(customOrderMapper.updateQuoteStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(customOrderMapper.findLinkByQuoteId(5L)).thenReturn(Optional.empty());

        customOrderService.acceptQuote(MEMBER_ID, 100L);

        ArgumentCaptor<CustomOrderPaymentLink> captor =
            ArgumentCaptor.forClass(CustomOrderPaymentLink.class);
        verify(customOrderMapper).insertPaymentLink(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
            .isEqualTo(LocalDate.of(2026, 8, 2).atStartOfDay());
    }

    @Test
    void acceptQuotePushesPickupWhenProducibleDateIsLater() {
        givenOrder(OrderStatus.UNDER_REVIEW); // 희망 픽업 8/5 14:00
        CustomOrderQuote quote = quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 20));
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.of(quote));
        when(customOrderMapper.updateQuoteStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(customOrderMapper.findLinkByQuoteId(5L)).thenReturn(Optional.empty());

        customOrderService.acceptQuote(MEMBER_ID, 100L);

        // 시각(14:00)은 유지하고 날짜만 제작 가능일로 민다
        verify(customOrderMapper).updatePickupAt(100L, LocalDateTime.of(2026, 8, 20, 14, 0));
    }

    @Test
    void acceptQuoteSnapsPickupToNextOpenDayWhenProducibleDateIsClosed() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        CustomOrderQuote quote = quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 16));
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.of(quote));
        when(customOrderMapper.updateQuoteStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(customOrderMapper.findLinkByQuoteId(5L)).thenReturn(Optional.empty());
        // 8/16은 휴무(슬롯 없음), 8/17부터 열린다
        when(storeService.getAvailablePickupSlots(eq(LocalDate.of(2026, 8, 16)), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
        when(storeService.getAvailablePickupSlots(eq(LocalDate.of(2026, 8, 17)), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(LocalDateTime.of(2026, 8, 17, 10, 0),
                LocalDateTime.of(2026, 8, 17, 14, 0)));

        customOrderService.acceptQuote(MEMBER_ID, 100L);

        // 휴무일을 건너뛰고 원래 시각(14:00)을 유지한 다음 영업일 슬롯으로 스냅한다
        verify(customOrderMapper).updatePickupAt(100L, LocalDateTime.of(2026, 8, 17, 14, 0));
    }

    @Test
    void acceptQuoteFailsWhenNoPickupSlotExists() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L))
            .thenReturn(Optional.of(quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 20))));
        when(customOrderMapper.updateQuoteStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(storeService.getAvailablePickupSlots(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());

        assertThatThrownBy(() -> customOrderService.acceptQuote(MEMBER_ID, 100L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.NO_PICKUP_SLOT);
    }

    @Test
    void acceptQuoteRejectsWhenAlreadyAccepted() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L))
            .thenReturn(Optional.of(quote(QuoteStatus.ACCEPTED, 180_000L, LocalDate.of(2026, 8, 10))));

        assertThatThrownBy(() -> customOrderService.acceptQuote(MEMBER_ID, 100L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.QUOTE_ALREADY_ACCEPTED);
    }

    @Test
    void acceptQuoteRejectsAfterPayment() {
        givenOrder(OrderStatus.IN_PRODUCTION);

        assertThatThrownBy(() -> customOrderService.acceptQuote(MEMBER_ID, 100L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.ALREADY_PAID);
    }

    @Test
    void acceptQuoteIsIdempotentWhenLinkAlreadyIssued() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L))
            .thenReturn(Optional.of(quote(QuoteStatus.SENT, 180_000L, LocalDate.of(2026, 8, 10))));
        when(customOrderMapper.updateQuoteStatus(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        CustomOrderPaymentLink existing = new CustomOrderPaymentLink();
        existing.setToken("existing-token");
        when(customOrderMapper.findLinkByQuoteId(5L)).thenReturn(Optional.of(existing));

        assertThat(customOrderService.acceptQuote(MEMBER_ID, 100L)).isEqualTo("existing-token");
        verify(customOrderMapper, never()).insertPaymentLink(any());
    }

    // ==================== 고객 취소 ====================

    @Test
    void cancelRevokesIssuedLinks() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuote(100L)).thenReturn(Optional.empty());
        when(orderMapper.cancelOrder(eq(100L), eq("UNDER_REVIEW"), anyString(), eq("CUSTOMER")))
            .thenReturn(1);

        customOrderService.cancelRequest(MEMBER_ID, 100L, "일정이 바뀌었습니다");

        verify(customOrderMapper).revokeIssuedLinks(100L, NOW);
    }

    @Test
    void cancelRejectedAfterQuoteAccepted() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuote(100L))
            .thenReturn(Optional.of(quote(QuoteStatus.ACCEPTED, 180_000L, LocalDate.of(2026, 8, 10))));

        assertThatThrownBy(() -> customOrderService.cancelRequest(MEMBER_ID, 100L, "변심"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.CANCEL_NOT_ALLOWED);
        verify(orderMapper, never()).cancelOrder(anyLong(), anyString(), anyString(), anyString());
    }

    // ==================== 파생 라벨 ====================

    @Test
    void progressLabelCombinesOrderAndQuoteState() {
        assertThat(CustomOrderService.progressLabel(OrderStatus.UNDER_REVIEW, null, null, NOW))
            .isEqualTo("검토 대기");
        assertThat(CustomOrderService.progressLabel(OrderStatus.UNDER_REVIEW,
            quote(QuoteStatus.SENT, 1L, LocalDate.now()), null, NOW)).isEqualTo("견적 발송됨");
        assertThat(CustomOrderService.progressLabel(OrderStatus.UNDER_REVIEW,
            quote(QuoteStatus.ACCEPTED, 1L, LocalDate.now()), null, NOW)).isEqualTo("결제 대기");
        // 결제 후에는 주문 상태 라벨이 그대로 쓰인다
        assertThat(CustomOrderService.progressLabel(OrderStatus.IN_PRODUCTION, null, null, NOW))
            .isEqualTo("제작 중");
    }

    @Test
    void progressLabelShowsExpiredLink() {
        CustomOrderPaymentLink link = new CustomOrderPaymentLink();
        link.setStatus(PaymentLinkStatus.ISSUED.name());
        link.setExpiresAt(NOW.minusHours(1));

        assertThat(CustomOrderService.progressLabel(OrderStatus.UNDER_REVIEW,
            quote(QuoteStatus.ACCEPTED, 1L, LocalDate.now()), link, NOW))
            .isEqualTo("결제 링크 만료");
    }

    // ==================== 헬퍼 ====================

    private void givenOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("CUS-20260801-ABC");
        order.setMemberId(MEMBER_ID);
        order.setStatus(status.name());
        order.setPickupAt(LocalDateTime.of(2026, 8, 5, 14, 0));
        order.setFinalAmount(62_000L);
        when(orderMapper.findByIdAndMemberId(100L, MEMBER_ID)).thenReturn(Optional.of(order));
    }

    private CustomOrderQuote quote(QuoteStatus status, long amount, LocalDate producibleDate) {
        CustomOrderQuote quote = new CustomOrderQuote();
        quote.setId(5L);
        quote.setOrderId(100L);
        quote.setVersion(1);
        quote.setQuotedAmount(amount);
        quote.setProducibleDate(producibleDate);
        quote.setStatus(status.name());
        quote.setSentAt(NOW);
        return quote;
    }

    private CustomOrderForm form(List<Long> optionIds) {
        CustomOrderForm form = new CustomOrderForm();
        form.setProductId(PRODUCT_ID);
        form.setOptionIds(optionIds);
        form.setLettering("Happy Birthday");
        form.setRequirements("장식 최소화");
        form.setPickupAt(LocalDateTime.of(2026, 8, 15, 14, 0));
        form.setDesiredBudget(150_000L);
        return form;
    }

    private MockMultipartFile jpg() {
        return new MockMultipartFile("referenceImages", "a.jpg", "image/jpeg", new byte[] {1});
    }

    private List<ProductOptionGroupView> optionGroups() {
        return List.of(
            new ProductOptionGroupView(1L, "크기", true, "SINGLE", List.of(
                new ProductOptionView(10L, "1호", 0L),
                new ProductOptionView(11L, "2호", 5_000L))),
            new ProductOptionGroupView(2L, "맛", true, "SINGLE", List.of(
                new ProductOptionView(20L, "바닐라", 0L),
                new ProductOptionView(21L, "초코", 2_000L))));
    }

    private ProductDetailView customProduct(boolean onSale) {
        return new ProductDetailView(PRODUCT_ID, "레터링 주문 케이크", "설명", 55_000L,
            "CUSTOM", "주문 제작", "ACTIVE", "판매 중", null, "주문 가능",
            5, 3, null, new BigDecimal("0.00"), 0, onSale);
    }

    private ProductDetailView generalProduct() {
        return new ProductDetailView(PRODUCT_ID, "딸기 케이크", "설명", 38_000L,
            "GENERAL", "일반 케이크", "ACTIVE", "판매 중", 10, "재고 있음",
            2, 2, null, new BigDecimal("0.00"), 0, true);
    }
}
