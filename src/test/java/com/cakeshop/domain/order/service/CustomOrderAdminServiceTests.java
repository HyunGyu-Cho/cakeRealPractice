package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.form.QuoteForm;
import com.cakeshop.domain.order.dto.form.RejectForm;
import com.cakeshop.domain.order.dto.view.CustomOrderAdminDetailView;
import com.cakeshop.domain.order.dto.view.CustomOrderDetailView;
import com.cakeshop.domain.order.dto.view.CustomOrderListView;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.Order;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOrderAdminServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Long ADMIN_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private CustomOrderMapper customOrderMapper;
    @Mock private CustomOrderService customOrderService;
    @Mock private MemberService memberService;
    @Mock private NotificationService notificationService;
    @Mock private ChatService chatService;

    private CustomOrderAdminService adminService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        adminService = new CustomOrderAdminService(orderMapper, customOrderMapper, customOrderService,
            memberService, notificationService, chatService, clock);
    }

    @Test
    void firstQuoteStartsAtVersionOne() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.empty());

        adminService.quote(100L, ADMIN_ID, quoteForm(180_000L, LocalDate.of(2026, 8, 12)));

        ArgumentCaptor<CustomOrderQuote> captor = ArgumentCaptor.forClass(CustomOrderQuote.class);
        verify(customOrderMapper).insertQuote(captor.capture());
        CustomOrderQuote saved = captor.getValue();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(QuoteStatus.SENT.name());
        assertThat(saved.getIssuedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void requoteSupersedesPreviousAndRevokesLinkBeforeInsert() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L))
            .thenReturn(Optional.of(quote(2, QuoteStatus.SENT)));

        adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 8, 12)));

        // 새 회차를 넣기 전에 이전 견적과 그 링크를 먼저 무효화해야 한다
        InOrder order = inOrder(customOrderMapper);
        order.verify(customOrderMapper).supersedeSentQuotes(100L);
        order.verify(customOrderMapper).revokeIssuedLinks(100L, NOW);
        order.verify(customOrderMapper).insertQuote(any());

        ArgumentCaptor<CustomOrderQuote> captor = ArgumentCaptor.forClass(CustomOrderQuote.class);
        verify(customOrderMapper).insertQuote(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }

    @Test
    void quoteRejectedWhenPreviousAlreadyAccepted() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L))
            .thenReturn(Optional.of(quote(1, QuoteStatus.ACCEPTED)));

        assertThatThrownBy(() ->
            adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 8, 12))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.QUOTE_ALREADY_ACCEPTED);
        verify(customOrderMapper, never()).insertQuote(any());
    }

    @Test
    void quoteRejectsPastProducibleDate() {
        givenOrder(OrderStatus.UNDER_REVIEW);

        assertThatThrownBy(() ->
            adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 8, 1))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.PRODUCIBLE_DATE_PASSED);
    }

    @Test
    void quoteRejectsProducibleDateBeyondPickupWindow() {
        givenOrder(OrderStatus.UNDER_REVIEW);

        // 주문제작 픽업 예약 창은 오늘(8/1) + 90일 = 10/30까지다. 그 뒤 날짜를 제시하면
        // 고객이 수락해도 잡을 픽업 슬롯이 없으므로 견적 단계에서 막는다.
        assertThatThrownBy(() ->
            adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 10, 31))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode",
                CustomOrderErrorCode.PRODUCIBLE_DATE_OUT_OF_WINDOW);
        verify(customOrderMapper, never()).insertQuote(any());
    }

    @Test
    void quoteAcceptsProducibleDateOnWindowBoundary() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.empty());

        // 경계값(오늘 + 90일)은 허용한다
        adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 10, 30)));

        verify(customOrderMapper).insertQuote(any());
    }

    @Test
    void quoteRejectedAfterPayment() {
        givenOrder(OrderStatus.IN_PRODUCTION);

        assertThatThrownBy(() ->
            adminService.quote(100L, ADMIN_ID, quoteForm(200_000L, LocalDate.of(2026, 8, 12))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.NOT_UNDER_REVIEW);
    }

    @Test
    void quoteNotifiesCustomerAndPostsChatCard() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.findLatestQuoteForUpdate(100L)).thenReturn(Optional.empty());

        adminService.quote(100L, ADMIN_ID, quoteForm(180_000L, LocalDate.of(2026, 8, 12)));

        verify(notificationService).notify(any());
        verify(chatService).postSystemCard(eq(9L), anyString(), eq("CUSTOM_ORDER_QUOTE"),
            eq("/orders/custom/100"));
    }

    @Test
    void rejectMarksFinalAndRevokesLinks() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.reject(100L, "UNDER_REVIEW", "제작이 어렵습니다")).thenReturn(1);

        RejectForm form = new RejectForm();
        form.setReason("제작이 어렵습니다");
        adminService.reject(100L, form);

        verify(customOrderMapper).supersedeSentQuotes(100L);
        verify(customOrderMapper).revokeIssuedLinks(100L, NOW);
        verify(notificationService).notify(any());
    }

    @Test
    void rejectFailsWhenStatusChangedConcurrently() {
        givenOrder(OrderStatus.UNDER_REVIEW);
        when(customOrderMapper.reject(anyLong(), anyString(), anyString())).thenReturn(0);

        RejectForm form = new RejectForm();
        form.setReason("제작이 어렵습니다");
        assertThatThrownBy(() -> adminService.reject(100L, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CustomOrderErrorCode.NOT_UNDER_REVIEW);
    }

    @Test
    void requestDetailCarriesOrderingMemberContact() {
        when(customOrderService.getRequest(100L)).thenReturn(detailView(9L));
        when(memberService.getProfileMap(List.of(9L))).thenReturn(Map.of(9L,
            new MemberProfileView(9L, "케이크러버", "lover@example.com", "010-1234-5678", NOW)));

        CustomOrderAdminDetailView detail = adminService.getRequestDetail(100L);

        assertThat(detail.memberId()).isEqualTo(9L);
        assertThat(detail.memberNickname()).isEqualTo("케이크러버");
        assertThat(detail.memberEmail()).isEqualTo("lover@example.com");
        assertThat(detail.memberPhone()).isEqualTo("010-1234-5678");
        assertThat(detail.request().orderNumber()).isEqualTo("CUS-20260801-ABC");
    }

    @Test
    void requestDetailFallsBackWhenMemberRowIsGone() {
        when(customOrderService.getRequest(100L)).thenReturn(detailView(9L));
        when(memberService.getProfileMap(List.of(9L))).thenReturn(Map.of());

        CustomOrderAdminDetailView detail = adminService.getRequestDetail(100L);

        assertThat(detail.memberNickname()).isEqualTo("탈퇴 회원");
        assertThat(detail.memberEmail()).isNull();
        assertThat(detail.memberPhone()).isNull();
    }

    @Test
    void listShowsFallbackNameWhenNicknameMissing() {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("CUS-20260801-ABC");
        order.setMemberId(9L);
        order.setStatus(OrderStatus.UNDER_REVIEW.name());
        order.setCreatedAt(NOW);
        when(customOrderMapper.countCustomOrders(null)).thenReturn(1L);
        when(customOrderMapper.findCustomOrderIdPage(null, 10, 0)).thenReturn(List.of(100L));
        when(orderMapper.findByIds(List.of(100L))).thenReturn(List.of(order));
        when(orderMapper.findItemsByOrderIds(List.of(100L))).thenReturn(List.of());
        when(customOrderMapper.findLatestQuotesByOrderIds(List.of(100L))).thenReturn(List.of());
        when(memberService.getNicknameMap(List.of(9L))).thenReturn(Map.of());

        PageResult<CustomOrderListView> page =
            adminService.getRequestPage(null, new PageRequest(1, 10));

        assertThat(page.getContent()).singleElement()
            .extracting(CustomOrderListView::memberName).isEqualTo("탈퇴 회원");
    }

    private CustomOrderDetailView detailView(Long memberId) {
        return new CustomOrderDetailView(100L, "CUS-20260801-ABC", memberId, "3호 생크림", null,
            List.of(), null, null, List.of(), NOW.plusDays(7), 150_000L, 150_000L, null,
            OrderStatus.UNDER_REVIEW.name(), "확인 중", "확인 중", null, List.of(), null,
            null, null, false, false, true, NOW);
    }

    private void givenOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("CUS-20260801-ABC");
        order.setMemberId(9L);
        order.setStatus(status.name());
        when(orderMapper.findById(100L)).thenReturn(Optional.of(order));
    }

    private CustomOrderQuote quote(int version, QuoteStatus status) {
        CustomOrderQuote quote = new CustomOrderQuote();
        quote.setId(5L);
        quote.setOrderId(100L);
        quote.setVersion(version);
        quote.setStatus(status.name());
        return quote;
    }

    private QuoteForm quoteForm(long amount, LocalDate producibleDate) {
        QuoteForm form = new QuoteForm();
        form.setQuotedAmount(amount);
        form.setProducibleDate(producibleDate);
        form.setAdminNote("데코 추가 반영");
        return form;
    }
}
