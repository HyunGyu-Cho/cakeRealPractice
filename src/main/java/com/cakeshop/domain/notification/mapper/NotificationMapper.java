package com.cakeshop.domain.notification.mapper;

import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    int insert(Notification notification);

    Optional<Notification> findById(@Param("id") Long id);

    /** 수신자별 최신순 키셋 조회. cursor가 null이면 첫 페이지, limit은 hasNext 판별을 위해 size+1로 넘긴다. */
    List<Notification> findSliceByReceiver(
        @Param("receiverId") Long receiverId,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );

    long countUnread(@Param("receiverId") Long receiverId);

    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    int markAllRead(@Param("receiverId") Long receiverId);

    long countBySearch(
        @Param("type") NotificationType type,
        @Param("read") Boolean read,
        @Param("receiverIds") List<Long> receiverIds
    );

    List<Notification> findSearchPage(
        @Param("type") NotificationType type,
        @Param("read") Boolean read,
        @Param("receiverIds") List<Long> receiverIds,
        @Param("size") int size,
        @Param("offset") int offset
    );
}
