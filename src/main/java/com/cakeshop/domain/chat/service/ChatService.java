package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.form.ChatMessageForm;
import com.cakeshop.domain.chat.dto.view.AdminChatPageView;
import com.cakeshop.domain.chat.dto.view.ChatImageView;
import com.cakeshop.domain.chat.dto.view.ChatMessageSliceView;
import com.cakeshop.domain.chat.dto.view.ChatMessageView;
import com.cakeshop.domain.chat.dto.view.ChatRoomDetailView;
import com.cakeshop.domain.chat.dto.view.ChatRoomSummaryRow;
import com.cakeshop.domain.chat.dto.view.ChatRoomSummaryView;
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
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.error.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String CUSTOM_ORDER_COMMAND = "/주문제작";
    private static final String CUSTOM_ORDER_ACTION = "/orders/custom/options";

    private final ChatMapper chatMapper;
    private final MemberService memberService;
    private final ChatImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    public ChatService(ChatMapper chatMapper, MemberService memberService,
                       ChatImageStorage imageStorage, ApplicationEventPublisher eventPublisher,
                       NotificationService notificationService) {
        this.chatMapper = chatMapper;
        this.memberService = memberService;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailView getCustomerRoom(Long customerId) {
        MemberProfileView customer = memberService.getProfile(customerId);
        return chatMapper.findRoomByCustomerId(customerId)
            .map(room -> detail(room, customer, recent(room.getId(), customerId, 50)))
            .orElseGet(() -> new ChatRoomDetailView(
                null, customerId, customer.nickname(), customer.email(),
                ChatRoomStatus.OPEN.name(), ChatRoomStatus.OPEN.label(), List.of()
            ));
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailView getAdminRoom(Long roomId, Long adminId) {
        ChatRoom room = requireRoom(roomId);
        MemberProfileView customer = memberService.getProfile(room.getCustomerId());
        return detail(room, customer, recent(roomId, adminId, 50));
    }

    @Transactional(readOnly = true)
    public ChatMessageSliceView getCustomerMessages(Long customerId, Long beforeId,
                                                    Long afterId, int requestedSize) {
        ChatRoom room = chatMapper.findRoomByCustomerId(customerId)
            .orElse(null);
        if (room == null) {
            return new ChatMessageSliceView(List.of(), false);
        }
        return messages(room.getId(), customerId, beforeId, afterId, requestedSize);
    }

    @Transactional(readOnly = true)
    public ChatMessageSliceView getAdminMessages(Long roomId, Long adminId, Long beforeId,
                                                 Long afterId, int requestedSize) {
        requireRoom(roomId);
        return messages(roomId, adminId, beforeId, afterId, requestedSize);
    }

    @Transactional(readOnly = true)
    public AdminChatPageView getAdminRooms(String keyword, String filter, int requestedPage,
                                           int requestedSize) {
        int page = Math.max(requestedPage, 0);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        String normalizedFilter = normalizeFilter(filter);
        List<Long> customerIds = null;
        if (StringUtils.hasText(keyword)) {
            customerIds = memberService.searchMemberIds(keyword);
            if (customerIds.isEmpty()) {
                return new AdminChatPageView(List.of(), page, 0, 0);
            }
        }

        int total = chatMapper.countRooms(normalizedFilter, customerIds);
        List<ChatRoomSummaryRow> rows = chatMapper.findRoomSummaries(
            normalizedFilter, customerIds, size, page * size);
        Map<Long, MemberProfileView> profiles = memberService.getProfileMap(
            rows.stream().map(ChatRoomSummaryRow::getCustomerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<ChatRoomSummaryView> rooms = rows.stream()
            .map(row -> summary(row, profiles.get(row.getCustomerId())))
            .toList();
        int totalPages = total == 0 ? 0 : (total + size - 1) / size;
        return new AdminChatPageView(rooms, page, totalPages, total);
    }

    @Transactional
    public ChatMessageView sendCustomerMessage(Long customerId, ChatMessageForm form) {
        PreparedMessage prepared = prepare(form);
        ChatRoom room = getOrCreateRoom(customerId);
        MemberProfileView customer = memberService.getProfile(customerId);

        ChatMessage existing = chatMapper.findMessageByClientMessageId(
            room.getId(), form.getClientMessageId().toString(), customerId).orElse(null);
        if (existing != null) {
            return ChatMessageView.from(existing);
        }

        boolean reopened = chatMapper.reopenRoom(room.getId()) > 0;
        ChatMessage message = newMessage(room.getId(), customerId, ChatSenderType.CUSTOMER,
            form, prepared, true);
        StoredMessage stored = storeMessage(message, customerId, prepared.image());
        if (!stored.created()) {
            return stored.view();
        }
        ChatMessageView created = stored.view();

        if (reopened) {
            eventPublisher.publishEvent(ChatEvent.status(
                room.getId(), customerId, customer.email(), ChatRoomStatus.OPEN.name()));
        }
        publishMessageEvents(room, customer, created);
        notifyAdminsOfCustomerMessage(customer, created);
        return created;
    }

    @Transactional
    public ChatMessageView sendAdminMessage(Long roomId, Long adminId, ChatMessageForm form) {
        PreparedMessage prepared = prepare(form);
        ChatRoom room = requireRoom(roomId);
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ChatErrorCode.ROOM_CLOSED);
        }
        MemberProfileView customer = memberService.getProfile(room.getCustomerId());

        ChatMessage existing = chatMapper.findMessageByClientMessageId(
            roomId, form.getClientMessageId().toString(), adminId).orElse(null);
        if (existing != null) {
            return ChatMessageView.from(existing);
        }

        ChatMessage message = newMessage(roomId, adminId, ChatSenderType.ADMIN,
            form, prepared, false);
        StoredMessage stored = storeMessage(message, adminId, prepared.image());
        if (!stored.created()) {
            return stored.view();
        }
        ChatMessageView created = stored.view();
        publishMessageEvents(room, customer, created);
        notifyCustomerOfAdminMessage(room.getCustomerId(), created);
        return created;
    }

    /**
     * [공개 계약] 상담 맥락에 시스템 카드를 남긴다. order(수제)의 견적·결제 링크 안내가 사용한다 —
     * 시그니처 변경 시 사용처(민정↔주환) 합의 필요.
     *
     * <p>{@code uk_chat_rooms_customer} 덕분에 고객당 방이 1개이므로 요청서에 방 id를 들고 다니지 않고
     * 고객 id로 찾는다. <b>방이 없으면 아무것도 하지 않는다</b> — 카드를 남길 상담 맥락 자체가 없는
     * 경우이며, 이 때문에 견적 발송이 실패하면 안 된다(알림은 별도 경로로 정상 발행된다).
     * 시스템 카드는 notification.md 규칙 4에 따라 알림을 발행하지 않는다.
     */
    @Transactional
    public void postSystemCard(Long customerId, String content, String actionType, String actionUrl) {
        ChatRoom room = chatMapper.findRoomByCustomerId(customerId).orElse(null);
        if (room == null) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setChatRoomId(room.getId());
        message.setClientMessageId(UUID.randomUUID().toString());
        message.setSenderId(null);
        message.setSenderType(ChatSenderType.SYSTEM);
        message.setMessageType(ChatMessageType.SYSTEM_CARD);
        message.setContent(content);
        message.setActionType(actionType);
        message.setActionUrl(actionUrl);

        StoredMessage stored = storeMessage(message, customerId, null);
        if (!stored.created()) {
            return;
        }
        MemberProfileView customer = memberService.getProfile(customerId);
        publishMessageEvents(room, customer, stored.view());
    }

    @Transactional
    public void readCustomerMessages(Long customerId, Long lastMessageId) {
        ChatRoom room = chatMapper.findRoomByCustomerId(customerId)
            .orElseThrow(() -> new BusinessException(ChatErrorCode.ROOM_NOT_FOUND));
        requireCursor(room.getId(), lastMessageId, customerId);
        chatMapper.markMessagesRead(
            room.getId(), customerId, lastMessageId, ChatSenderType.ADMIN);
        MemberProfileView customer = memberService.getProfile(customerId);
        eventPublisher.publishEvent(ChatEvent.read(
            room.getId(), customerId, customer.email(), lastMessageId));
        eventPublisher.publishEvent(ChatEvent.summary(
            room.getId(), customerId, customer.email()));
    }

    @Transactional
    public void readAdminMessages(Long roomId, Long adminId, Long lastMessageId) {
        ChatRoom room = requireRoom(roomId);
        requireCursor(roomId, lastMessageId, adminId);
        chatMapper.markMessagesRead(
            roomId, adminId, lastMessageId, ChatSenderType.CUSTOMER);
        MemberProfileView customer = memberService.getProfile(room.getCustomerId());
        eventPublisher.publishEvent(ChatEvent.read(
            roomId, room.getCustomerId(), customer.email(), lastMessageId));
        eventPublisher.publishEvent(ChatEvent.summary(
            roomId, room.getCustomerId(), customer.email()));
    }

    @Transactional
    public void closeRoom(Long roomId) {
        ChatRoom room = requireRoom(roomId);
        if (chatMapper.closeRoom(roomId) > 0) {
            MemberProfileView customer = memberService.getProfile(room.getCustomerId());
            eventPublisher.publishEvent(ChatEvent.status(
                roomId, room.getCustomerId(), customer.email(), ChatRoomStatus.CLOSED.name()));
            eventPublisher.publishEvent(ChatEvent.summary(
                roomId, room.getCustomerId(), customer.email()));
        }
    }

    @Transactional(readOnly = true)
    public ChatImageView getImage(Long messageId, Long requesterId, boolean admin) {
        ChatMessage message = chatMapper.findMessageById(messageId, requesterId)
            .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        ChatRoom room = requireRoom(message.getChatRoomId());
        if (!admin && !room.getCustomerId().equals(requesterId)) {
            throw new BusinessException(ChatErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(message.getImageKey())) {
            throw new BusinessException(ChatErrorCode.IMAGE_NOT_FOUND);
        }
        try {
            return imageStorage.load(
                message.getImageKey(), message.getImageContentType(),
                message.getImageOriginalName(), message.getImageSize());
        } catch (IllegalStateException e) {
            throw new BusinessException(ChatErrorCode.IMAGE_NOT_FOUND);
        }
    }

    private ChatMessageSliceView messages(Long roomId, Long viewerId, Long beforeId,
                                          Long afterId, int requestedSize) {
        if (beforeId != null && afterId != null) {
            throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
        }
        int size = Math.min(Math.max(requestedSize, 1), 100);
        int querySize = size + 1;
        List<ChatMessage> found;
        boolean descending;
        if (afterId != null) {
            found = new ArrayList<>(
                chatMapper.findMessagesAfter(roomId, afterId, viewerId, querySize));
            descending = false;
        } else if (beforeId != null) {
            found = new ArrayList<>(
                chatMapper.findMessagesBefore(roomId, beforeId, viewerId, querySize));
            descending = true;
        } else {
            found = new ArrayList<>(
                chatMapper.findRecentMessages(roomId, viewerId, querySize));
            descending = true;
        }
        boolean hasMore = found.size() > size;
        if (hasMore) {
            found.remove(found.size() - 1);
        }
        if (descending) {
            Collections.reverse(found);
        }
        return new ChatMessageSliceView(
            found.stream().map(ChatMessageView::from).toList(), hasMore);
    }

    private List<ChatMessageView> recent(Long roomId, Long viewerId, int size) {
        List<ChatMessage> messages = new ArrayList<>(
            chatMapper.findRecentMessages(roomId, viewerId, size));
        Collections.reverse(messages);
        return messages.stream().map(ChatMessageView::from).toList();
    }

    private ChatRoom getOrCreateRoom(Long customerId) {
        ChatRoom existing = chatMapper.findRoomByCustomerId(customerId).orElse(null);
        if (existing != null) {
            return existing;
        }
        ChatRoom room = new ChatRoom();
        room.setCustomerId(customerId);
        room.setStatus(ChatRoomStatus.OPEN);
        try {
            chatMapper.insertRoom(room);
            return room;
        } catch (DuplicateKeyException e) {
            return chatMapper.findRoomByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.DUPLICATE_CONFLICT));
        }
    }

    private StoredMessage storeMessage(ChatMessage message, Long viewerId, MultipartFile image) {
        String storedKey = null;
        if (message.getMessageType() == ChatMessageType.IMAGE) {
            storedKey = imageStorage.store(image, message.getImageContentType());
            message.setImageKey(storedKey);
            registerRollbackDelete(storedKey);
        }
        try {
            chatMapper.insertMessage(message);
        } catch (DuplicateKeyException e) {
            deleteQuietly(storedKey);
            ChatMessageView existing = chatMapper.findMessageByClientMessageId(
                    message.getChatRoomId(), message.getClientMessageId(), viewerId)
                .map(ChatMessageView::from)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.DUPLICATE_CONFLICT));
            return new StoredMessage(existing, false);
        }
        chatMapper.updateLastMessage(message.getChatRoomId());
        ChatMessageView created = chatMapper.findMessageById(message.getId(), viewerId)
            .map(ChatMessageView::from)
            .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        return new StoredMessage(created, true);
    }

    private ChatMessage newMessage(Long roomId, Long senderId, ChatSenderType senderType,
                                   ChatMessageForm form, PreparedMessage prepared,
                                   boolean customerCommandEnabled) {
        ChatMessage message = new ChatMessage();
        message.setChatRoomId(roomId);
        message.setClientMessageId(form.getClientMessageId().toString());

        if (customerCommandEnabled && prepared.command()) {
            message.setSenderId(null);
            message.setSenderType(ChatSenderType.SYSTEM);
            message.setMessageType(ChatMessageType.SYSTEM_CARD);
            message.setContent("주문제작 케이크 요청을 시작해 보세요.");
            message.setActionType("CUSTOM_ORDER_START");
            message.setActionUrl(CUSTOM_ORDER_ACTION);
            return message;
        }

        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setMessageType(prepared.image() == null
            ? ChatMessageType.TEXT : ChatMessageType.IMAGE);
        message.setContent(prepared.content());
        if (prepared.image() != null) {
            message.setImageOriginalName(safeOriginalName(prepared.image().getOriginalFilename()));
            message.setImageContentType(prepared.contentType());
            message.setImageSize(prepared.image().getSize());
        }
        return message;
    }

    private PreparedMessage prepare(ChatMessageForm form) {
        String content = form.getContent() == null ? null : form.getContent().trim();
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ChatErrorCode.CONTENT_TOO_LONG);
        }
        MultipartFile image = form.getImage();
        if (image != null && image.isEmpty()) {
            image = null;
        }
        if (!StringUtils.hasText(content) && image == null) {
            throw new BusinessException(ChatErrorCode.EMPTY_MESSAGE);
        }
        String contentType = null;
        if (image != null) {
            contentType = validateImage(image);
        }
        boolean command = CUSTOM_ORDER_COMMAND.equals(content) && image == null;
        return new PreparedMessage(StringUtils.hasText(content) ? content : null,
            image, contentType, command);
    }

    private String validateImage(MultipartFile image) {
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ChatErrorCode.IMAGE_TOO_LARGE);
        }
        String declared = image.getContentType();
        if (!"image/jpeg".equals(declared) && !"image/png".equals(declared)) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
        byte[] header = new byte[8];
        int read;
        try (InputStream input = image.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
        boolean jpeg = read >= 3
            && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
            && (header[2] & 0xff) == 0xff;
        boolean png = read >= 8
            && (header[0] & 0xff) == 0x89 && header[1] == 0x50
            && header[2] == 0x4e && header[3] == 0x47
            && header[4] == 0x0d && header[5] == 0x0a
            && header[6] == 0x1a && header[7] == 0x0a;
        if (("image/jpeg".equals(declared) && !jpeg)
            || ("image/png".equals(declared) && !png)) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
        return declared;
    }

    private void requireCursor(Long roomId, Long messageId, Long viewerId) {
        ChatMessage message = chatMapper.findMessageById(messageId, viewerId)
            .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (!roomId.equals(message.getChatRoomId())) {
            throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
        }
    }

    private ChatRoom requireRoom(Long roomId) {
        return chatMapper.findRoomById(roomId)
            .orElseThrow(() -> new BusinessException(ChatErrorCode.ROOM_NOT_FOUND));
    }

    private ChatRoomDetailView detail(ChatRoom room, MemberProfileView customer,
                                      List<ChatMessageView> messages) {
        return new ChatRoomDetailView(
            room.getId(), room.getCustomerId(), customer.nickname(), customer.email(),
            room.getStatus().name(), room.getStatus().label(), messages);
    }

    private ChatRoomSummaryView summary(ChatRoomSummaryRow row, MemberProfileView customer) {
        String name = customer == null ? "알 수 없는 고객" : customer.nickname();
        String email = customer == null ? "" : customer.email();
        return new ChatRoomSummaryView(
            row.getRoomId(), row.getCustomerId(), name, email,
            row.getStatus().name(), row.getStatus().label(), row.isUnanswered(),
            row.getUnreadCount(), row.getLastMessageId(), row.getLastMessagePreview(),
            row.getLastMessageAt());
    }

    private String normalizeFilter(String filter) {
        if ("UNANSWERED".equalsIgnoreCase(filter)) {
            return "UNANSWERED";
        }
        if ("CLOSED".equalsIgnoreCase(filter)) {
            return "CLOSED";
        }
        return "ALL";
    }

    private void publishMessageEvents(ChatRoom room, MemberProfileView customer,
                                      ChatMessageView message) {
        eventPublisher.publishEvent(ChatEvent.message(
            room.getId(), room.getCustomerId(), customer.email(), message));
        eventPublisher.publishEvent(ChatEvent.summary(
            room.getId(), room.getCustomerId(), customer.email()));
    }

    /** 알림 발행은 종단 도메인의 공개 계약만 호출한다(역참조 금지). SYSTEM_CARD는 알리지 않는다. */
    private void notifyCustomerOfAdminMessage(Long customerId, ChatMessageView message) {
        if (ChatMessageType.SYSTEM_CARD.name().equals(message.messageType())) {
            return;
        }
        notificationService.notify(NotificationCommand.forChat(
            customerId, NotificationType.CHAT_MESSAGE, message.id(),
            NotificationType.CHAT_MESSAGE.label(), preview(message), "/chat"));
    }

    private void notifyAdminsOfCustomerMessage(MemberProfileView customer,
                                               ChatMessageView message) {
        if (ChatMessageType.SYSTEM_CARD.name().equals(message.messageType())) {
            return;
        }
        notificationService.notifyAdmins(NotificationCommand.toAdmins(
            NotificationType.ADMIN_CHAT_MESSAGE,
            customer.nickname() + "님의 문의", preview(message),
            "/admin/chat?roomId=" + message.roomId(), null, message.id()));
    }

    private String preview(ChatMessageView message) {
        if (ChatMessageType.IMAGE.name().equals(message.messageType())
            && !StringUtils.hasText(message.content())) {
            return "이미지를 보냈습니다.";
        }
        String content = message.content();
        return content.length() <= 50 ? content : content.substring(0, 50) + "…";
    }

    private void registerRollbackDelete(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("롤백 채팅 이미지 삭제에 실패했습니다. key={}", key, e);
        }
    }

    private String safeOriginalName(String originalName) {
        String safe = StringUtils.getFilename(originalName);
        if (!StringUtils.hasText(safe)) {
            return "chat-image";
        }
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    private record PreparedMessage(
        String content,
        MultipartFile image,
        String contentType,
        boolean command
    ) {
    }

    private record StoredMessage(ChatMessageView view, boolean created) {
    }
}
