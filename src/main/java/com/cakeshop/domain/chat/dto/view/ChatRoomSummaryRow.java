package com.cakeshop.domain.chat.dto.view;

import com.cakeshop.domain.chat.entity.ChatRoomStatus;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRoomSummaryRow {
    private Long roomId;
    private Long customerId;
    private ChatRoomStatus status;
    private LocalDateTime lastMessageAt;
    private Long lastMessageId;
    private String lastMessagePreview;
    private int unreadCount;
    private boolean unanswered;
}
