package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.notification.dto.view.NotificationDeliveryRetryRow;
import com.cakeshop.domain.notification.entity.DeliveryChannel;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.NotificationDelivery;
import com.cakeshop.domain.notification.error.NotificationErrorCode;
import com.cakeshop.domain.notification.mapper.NotificationDeliveryMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 전달 이력. 업무 트랜잭션에 남기는 것은 {@link #enqueue} INSERT 한 건뿐이고,
 * 전송 결과 기록은 커밋 후(브로드캐스터)·스케줄러에서 독립 트랜잭션으로 처리한다.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    /**
     * 스케줄러 재시도 상한. 즉시 푸시 실패는 재시도 횟수에 포함하지 않으므로
     * 전달 시도는 최대 (즉시 1회 + 재시도 3회)다. 누적 재시도가 이 값에 도달하면 ABANDONED.
     */
    public static final int MAX_RETRY_COUNT = 3;
    /** 백오프 기본 간격. 누적 재시도 n회 다음 시도는 BASE * 2^n 뒤다(1·2·4분). */
    private static final long BACKOFF_BASE_MINUTES = 1L;
    /** failure_reason 컬럼 길이. 넘치면 UPDATE 가 매번 실패해 재시도가 무한 반복된다. */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final int FAILURE_CODE_MAX_LENGTH = 100;

    private final NotificationDeliveryMapper deliveryMapper;

    public NotificationDeliveryService(NotificationDeliveryMapper deliveryMapper) {
        this.deliveryMapper = deliveryMapper;
    }

    /**
     * 업무 트랜잭션에 참여하는 유일한 쓰기. 반드시 알림 저장과 같은 트랜잭션에서 호출한다
     * ({@code MANDATORY}가 이를 코드로 강제한다).
     * 커밋 직후 즉시 푸시가 유실되는 경우를 대비해 {@code nextRetryAt}을 미리 채워
     * 스케줄러가 한 주기 뒤 같은 조건으로 집어가게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueue(Long notificationId) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotificationId(notificationId);
        delivery.setChannel(DeliveryChannel.WEBSOCKET);
        delivery.setStatus(DeliveryStatus.REQUESTED);
        delivery.setNextRetryAt(nextRetryAt(0));
        deliveryMapper.insert(delivery);
        return delivery.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long deliveryId, DeliveryStatus from, String recipient) {
        requireTransition(from, DeliveryStatus.SENT);
        int updated = deliveryMapper.markSent(deliveryId, DeliveryStatus.SENT, recipient, from);
        if (updated == 0) {
            // 다른 경로가 먼저 처리한 행이다. 실패로 볼 일이 아니므로 조용히 넘어간다.
            log.debug("이미 처리된 전달 건이라 SENT 기록을 건너뜁니다. deliveryId={}", deliveryId);
        }
    }

    /**
     * 커밋 직후 즉시 푸시의 실패. 아직 재시도를 한 번도 쓰지 않았으므로
     * {@code retry_count}는 0으로 두고 첫 재시도 시각만 잡는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInitialFailure(Long deliveryId, DeliveryStatus from,
                                   String failureCode, String failureReason) {
        markFailure(deliveryId, from, 0, failureCode, failureReason);
    }

    /**
     * 재시도 실패 기록. {@code retryCount}는 이번 시도까지 포함한 누적 재시도 횟수이며,
     * 상한에 도달하면 {@code ABANDONED}로, 아니면 {@code FAILED} + 다음 시도 시각을 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryFailure(Long deliveryId, DeliveryStatus from, int previousRetryCount,
                                 String failureCode, String failureReason) {
        markFailure(deliveryId, from, previousRetryCount + 1, failureCode, failureReason);
    }

    private void markFailure(Long deliveryId, DeliveryStatus from, int retryCount,
                             String failureCode, String failureReason) {
        boolean exhausted = retryCount >= MAX_RETRY_COUNT;
        DeliveryStatus next = exhausted ? DeliveryStatus.ABANDONED : DeliveryStatus.FAILED;
        requireTransition(from, next);

        int updated = deliveryMapper.markFailed(
            deliveryId,
            next,
            retryCount,
            truncate(failureCode, FAILURE_CODE_MAX_LENGTH),
            truncate(failureReason, FAILURE_REASON_MAX_LENGTH),
            exhausted ? null : nextRetryAt(retryCount),
            from);
        if (updated == 0) {
            log.debug("이미 처리된 전달 건이라 실패 기록을 건너뜁니다. deliveryId={}", deliveryId);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryRetryRow> findRetryTargets(int limit) {
        return deliveryMapper.findRetryTargets(MAX_RETRY_COUNT, limit);
    }

    private void requireTransition(DeliveryStatus from, DeliveryStatus to) {
        if (from == null || !from.canTransitionTo(to)) {
            throw new BusinessException(NotificationErrorCode.INVALID_DELIVERY_TRANSITION);
        }
    }

    private LocalDateTime nextRetryAt(int attempted) {
        return LocalDateTime.now().plusMinutes(BACKOFF_BASE_MINUTES * (1L << attempted));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
