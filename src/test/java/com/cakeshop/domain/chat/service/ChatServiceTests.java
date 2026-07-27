package com.cakeshop.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.global.infra.ImageValidator;
import com.cakeshop.domain.chat.dto.form.ChatMessageForm;
import com.cakeshop.domain.chat.dto.view.ChatMessageView;
import com.cakeshop.domain.chat.entity.ChatMessage;
import com.cakeshop.domain.chat.entity.ChatMessageType;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.entity.ChatRoomStatus;
import com.cakeshop.domain.chat.entity.ChatSenderType;
import com.cakeshop.domain.chat.error.ChatErrorCode;
import com.cakeshop.domain.chat.event.ChatEvent;
import com.cakeshop.domain.chat.infra.ChatImageStorage;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ChatServiceTests {

    @Mock private ChatMapper chatMapper;
    @Mock private MemberService memberService;
    @Mock private ChatImageStorage imageStorage;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

    private ChatService chatService;
    private AtomicReference<ChatMessage> inserted;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMapper, memberService, imageStorage, eventPublisher,
            notificationService, new ImageValidator());
        inserted = new AtomicReference<>();
        org.mockito.Mockito.lenient().when(memberService.getProfile(1L)).thenReturn(
            new MemberProfileView(1L, "고객", "customer@example.com", null, LocalDateTime.now()));
    }

    @Test
    void firstCustomerMessageCreatesSinglePermanentRoomAndMessage() {
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(10L);
            return 1;
        }).when(chatMapper).insertRoom(any(ChatRoom.class));
        givenMessageInsert();

        ChatMessageView result = chatService.sendCustomerMessage(1L, form("안녕하세요"));

        assertThat(result.roomId()).isEqualTo(10L);
        assertThat(result.senderType()).isEqualTo("CUSTOMER");
        verify(chatMapper).insertRoom(any(ChatRoom.class));
        verify(chatMapper).insertMessage(any(ChatMessage.class));
        verify(chatMapper).updateLastMessage(10L);
    }

    @Test
    void repeatedClientUuidReturnsExistingMessageWithoutInsert() {
        ChatRoom room = room(ChatRoomStatus.OPEN);
        ChatMessage existing = message(50L, ChatSenderType.CUSTOMER, ChatMessageType.TEXT);
        UUID clientId = UUID.randomUUID();
        existing.setClientMessageId(clientId.toString());
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.of(room));
        when(chatMapper.findMessageByClientMessageId(10L, clientId.toString(), 1L))
            .thenReturn(Optional.of(existing));
        ChatMessageForm form = form("재전송");
        form.setClientMessageId(clientId);

        ChatMessageView result = chatService.sendCustomerMessage(1L, form);

        assertThat(result.id()).isEqualTo(50L);
        verify(chatMapper, never()).insertMessage(any());
        verify(imageStorage, never()).store(any(), any());
    }

    @Test
    void concurrentImageRetryDeletesOnlyTheLosingStoredFile() {
        ChatRoom room = room(ChatRoomStatus.OPEN);
        ChatMessage existing = message(51L, ChatSenderType.CUSTOMER, ChatMessageType.IMAGE);
        ChatMessageForm form = form("참고 이미지");
        existing.setClientMessageId(form.getClientMessageId().toString());
        existing.setImageKey("existing.png");
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.of(room));
        when(chatMapper.findMessageByClientMessageId(
                10L, form.getClientMessageId().toString(), 1L))
            .thenReturn(Optional.empty(), Optional.of(existing));
        form.setImage(new MockMultipartFile(
            "image", "reference.png", "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
        when(imageStorage.store(any(), eq("image/png"))).thenReturn("losing.png");
        org.mockito.Mockito.doThrow(new DuplicateKeyException("race"))
            .when(chatMapper).insertMessage(any(ChatMessage.class));

        ChatMessageView result = chatService.sendCustomerMessage(1L, form);

        assertThat(result.id()).isEqualTo(51L);
        verify(imageStorage).delete("losing.png");
        verify(chatMapper, never()).updateLastMessage(anyLong());
    }

    @Test
    void customOrderCommandBecomesSystemCardAndDoesNotCreateOrdinaryMessage() {
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.of(room(ChatRoomStatus.OPEN)));
        givenMessageInsert();

        ChatMessageView result = chatService.sendCustomerMessage(1L, form("/주문제작"));

        assertThat(result.senderType()).isEqualTo("SYSTEM");
        assertThat(result.messageType()).isEqualTo("SYSTEM_CARD");
        assertThat(result.actionUrl()).isEqualTo("/orders/custom/options");
        assertThat(inserted.get().getSenderId()).isNull();
    }

    @Test
    void customerActivityReopensClosedRoom() {
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.of(room(ChatRoomStatus.CLOSED)));
        when(chatMapper.reopenRoom(10L)).thenReturn(1);
        givenMessageInsert();

        chatService.sendCustomerMessage(1L, form("상담을 다시 시작할게요"));

        verify(chatMapper).reopenRoom(10L);
        verify(eventPublisher, org.mockito.Mockito.atLeast(1))
            .publishEvent(any(ChatEvent.class));
    }

    @Test
    void adminCannotSendToClosedRoom() {
        when(chatMapper.findRoomById(10L)).thenReturn(Optional.of(room(ChatRoomStatus.CLOSED)));

        assertChatError(
            () -> chatService.sendAdminMessage(10L, 99L, form("답변")),
            ChatErrorCode.ROOM_CLOSED
        );
        verify(chatMapper, never()).insertMessage(any());
    }

    @Test
    void rejectsSpoofedOrOversizedImage() {
        ChatMessageForm spoofed = form("");
        spoofed.setImage(new MockMultipartFile(
            "image", "fake.jpg", "image/jpeg", "not-an-image".getBytes()));

        assertChatError(
            () -> chatService.sendCustomerMessage(1L, spoofed),
            ChatErrorCode.INVALID_IMAGE_TYPE
        );

        ChatMessageForm oversized = form("");
        oversized.setImage(new MockMultipartFile(
            "image", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]));
        assertChatError(
            () -> chatService.sendCustomerMessage(1L, oversized),
            ChatErrorCode.IMAGE_TOO_LARGE
        );
        verify(chatMapper, never()).insertMessage(any());
    }

    @Test
    void customerMessageNotifiesAdminsAndSystemCardDoesNot() {
        when(chatMapper.findRoomByCustomerId(1L)).thenReturn(Optional.of(room(ChatRoomStatus.OPEN)));
        givenMessageInsert();

        chatService.sendCustomerMessage(1L, form("케이크 문의드려요"));
        verify(notificationService).notifyAdmins(any(NotificationCommand.class));

        chatService.sendCustomerMessage(1L, form("/주문제작"));
        verify(notificationService, org.mockito.Mockito.times(1))
            .notifyAdmins(any(NotificationCommand.class));
        verify(notificationService, never()).notify(any(NotificationCommand.class));
    }

    @Test
    void adminMessageNotifiesTheRoomCustomer() {
        when(chatMapper.findRoomById(10L)).thenReturn(Optional.of(room(ChatRoomStatus.OPEN)));
        givenMessageInsert();

        chatService.sendAdminMessage(10L, 99L, form("확인해 드릴게요"));

        org.mockito.ArgumentCaptor<NotificationCommand> captor =
            org.mockito.ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(captor.capture());
        assertThat(captor.getValue().receiverId()).isEqualTo(1L);
        assertThat(captor.getValue().targetUrl()).isEqualTo("/chat");
        verify(notificationService, never()).notifyAdmins(any(NotificationCommand.class));
    }

    @Test
    void blankMessageIsRejected() {
        assertChatError(
            () -> chatService.sendCustomerMessage(1L, form("   ")),
            ChatErrorCode.EMPTY_MESSAGE
        );
    }

    private void givenMessageInsert() {
        when(chatMapper.findMessageByClientMessageId(eq(10L), any(String.class), anyLong()))
            .thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(100L);
            message.setCreatedAt(LocalDateTime.now());
            inserted.set(message);
            return 1;
        }).when(chatMapper).insertMessage(any(ChatMessage.class));
        when(chatMapper.findMessageById(eq(100L), anyLong()))
            .thenAnswer(invocation -> Optional.of(inserted.get()));
    }

    private ChatRoom room(ChatRoomStatus status) {
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setCustomerId(1L);
        room.setStatus(status);
        return room;
    }

    private ChatMessage message(Long id, ChatSenderType senderType, ChatMessageType type) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setChatRoomId(10L);
        message.setSenderId(1L);
        message.setSenderType(senderType);
        message.setMessageType(type);
        message.setContent("기존 메시지");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private ChatMessageForm form(String content) {
        ChatMessageForm form = new ChatMessageForm();
        form.setClientMessageId(UUID.randomUUID());
        form.setContent(content);
        return form;
    }

    private void assertChatError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }
}
