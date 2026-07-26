package com.cakeshop.domain.notification.dto.view;

import java.util.List;

/** 최신순 키셋 슬라이스. nextCursor는 다음 요청의 cursor 파라미터로 그대로 넘긴다. */
public record NotificationSliceView(
    List<NotificationView> content,
    boolean hasNext,
    Long nextCursor,
    long unreadCount
) {
}
