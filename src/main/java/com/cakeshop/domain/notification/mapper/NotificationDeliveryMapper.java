package com.cakeshop.domain.notification.mapper;

import com.cakeshop.domain.notification.dto.view.NotificationDeliveryRetryRow;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationDelivery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationDeliveryMapper {

    int insert(NotificationDelivery delivery);

    Optional<NotificationDelivery> findById(@Param("id") Long id);

    /** 재시도 대기 중인 전달 건. 오래된 것부터. */
    List<NotificationDeliveryRetryRow> findRetryTargets(@Param("maxRetryCount") int maxRetryCount,
                                                        @Param("limit") int limit);

    /**
     * {@code fromStatus} 가드가 낙관적 잠금 역할을 한다 —
     * 즉시 푸시와 스케줄러가 같은 행을 동시에 건드려도 한쪽만 성공한다.
     */
    int markSent(@Param("id") Long id,
                 @Param("status") DeliveryStatus status,
                 @Param("recipient") String recipient,
                 @Param("fromStatus") DeliveryStatus fromStatus);

    int markFailed(@Param("id") Long id,
                   @Param("status") DeliveryStatus status,
                   @Param("retryCount") int retryCount,
                   @Param("failureCode") String failureCode,
                   @Param("failureReason") String failureReason,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("fromStatus") DeliveryStatus fromStatus);
}
