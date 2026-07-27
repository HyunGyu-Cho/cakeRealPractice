package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.WebhookEvent;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WebhookEventMapper {
    /** 전송 ID UNIQUE 충돌은 재전송이라는 뜻이다. 호출측이 {@code DuplicateKeyException}을 잡는다. */
    int insertEvent(WebhookEvent event);

    Optional<WebhookEvent> findByEventId(@Param("eventId") String eventId);

    /** 아직 반영하지 않은 이벤트를 오래된 순으로. */
    List<WebhookEvent> findUnprocessed(@Param("limit") int limit);

    int markProcessed(@Param("id") Long id, @Param("processStatus") String processStatus,
                      @Param("failReason") String failReason);
}
