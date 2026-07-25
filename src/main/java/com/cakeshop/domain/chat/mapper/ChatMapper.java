package com.cakeshop.domain.chat.mapper;

import com.cakeshop.domain.chat.dto.view.ChatRoomSummaryRow;
import com.cakeshop.domain.chat.entity.ChatMessage;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.entity.ChatSenderType;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMapper {

    Optional<ChatRoom> findRoomByCustomerId(@Param("customerId") Long customerId);

    Optional<ChatRoom> findRoomById(@Param("roomId") Long roomId);

    int insertRoom(ChatRoom room);

    int reopenRoom(@Param("roomId") Long roomId);

    int closeRoom(@Param("roomId") Long roomId);

    Optional<ChatMessage> findMessageByClientMessageId(
        @Param("roomId") Long roomId,
        @Param("clientMessageId") String clientMessageId,
        @Param("viewerId") Long viewerId
    );

    Optional<ChatMessage> findMessageById(
        @Param("messageId") Long messageId,
        @Param("viewerId") Long viewerId
    );

    int insertMessage(ChatMessage message);

    int updateLastMessage(@Param("roomId") Long roomId);

    List<ChatMessage> findRecentMessages(
        @Param("roomId") Long roomId,
        @Param("viewerId") Long viewerId,
        @Param("limit") int limit
    );

    List<ChatMessage> findMessagesBefore(
        @Param("roomId") Long roomId,
        @Param("beforeId") Long beforeId,
        @Param("viewerId") Long viewerId,
        @Param("limit") int limit
    );

    List<ChatMessage> findMessagesAfter(
        @Param("roomId") Long roomId,
        @Param("afterId") Long afterId,
        @Param("viewerId") Long viewerId,
        @Param("limit") int limit
    );

    int markMessagesRead(
        @Param("roomId") Long roomId,
        @Param("readerId") Long readerId,
        @Param("lastMessageId") Long lastMessageId,
        @Param("senderType") ChatSenderType senderType
    );

    int countRooms(
        @Param("filter") String filter,
        @Param("customerIds") List<Long> customerIds
    );

    List<ChatRoomSummaryRow> findRoomSummaries(
        @Param("filter") String filter,
        @Param("customerIds") List<Long> customerIds,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
