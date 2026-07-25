package com.cakeshop.domain.chat.dto.view;

import java.util.List;

public record ChatMessageSliceView(
    List<ChatMessageView> messages,
    boolean hasMore
) {
}
