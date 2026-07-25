package com.cakeshop.domain.chat.dto.view;

import java.util.List;

public record AdminChatPageView(
    List<ChatRoomSummaryView> rooms,
    int page,
    int totalPages,
    long totalElements
) {
}
