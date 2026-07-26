package com.cakeshop.domain.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.notification.dto.view.NotificationDeliveryRetryRow;
import com.cakeshop.domain.notification.entity.DeliveryChannel;
import com.cakeshop.domain.notification.entity.DeliveryStatus;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationDelivery;
import com.cakeshop.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전달 이력 SQL 을 실제 MariaDB 에 대고 검증한다.
 * 재시도 조회는 스케줄러만 쓰는 경로라 단위 테스트로는 컬럼·매핑 오류가 드러나지 않는다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NotificationDeliveryMapperTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired NotificationMapper notificationMapper;
    @Autowired NotificationDeliveryMapper deliveryMapper;

    @Test
    void retryTargetIsFoundAndThenClearedBySent() {
        Long notificationId = givenNotification();
        Long deliveryId = givenDelivery(notificationId, LocalDateTime.now().minusMinutes(1));

        List<NotificationDeliveryRetryRow> targets = deliveryMapper.findRetryTargets(3, 100);

        assertThat(targets).extracting(NotificationDeliveryRetryRow::deliveryId)
            .contains(deliveryId);
        NotificationDeliveryRetryRow target = targets.stream()
            .filter(row -> row.deliveryId().equals(deliveryId))
            .findFirst()
            .orElseThrow();
        assertThat(target.status()).isEqualTo(DeliveryStatus.REQUESTED);
        assertThat(target.retryCount()).isZero();
        assertThat(target.receiverId()).isNotNull();
        assertThat(target.notificationType()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(target.read()).isFalse();
        assertThat(target.targetUrl()).isEqualTo("/orders/1");

        int updated = deliveryMapper.markSent(
            deliveryId, DeliveryStatus.SENT, "customer@example.com", DeliveryStatus.REQUESTED);

        assertThat(updated).isEqualTo(1);
        NotificationDelivery sent = deliveryMapper.findById(deliveryId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(sent.getRecipient()).isEqualTo("customer@example.com");
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getNextRetryAt()).isNull();
        assertThat(deliveryMapper.findRetryTargets(3, 100))
            .extracting(NotificationDeliveryRetryRow::deliveryId)
            .doesNotContain(deliveryId);
    }

    /** 다른 경로가 먼저 처리한 행은 fromStatus 가드에 걸려 갱신되지 않는다. */
    @Test
    void guardOnFromStatusPreventsDoubleUpdate() {
        Long deliveryId = givenDelivery(givenNotification(), LocalDateTime.now());
        deliveryMapper.markSent(deliveryId, DeliveryStatus.SENT, "first@example.com",
            DeliveryStatus.REQUESTED);

        int updated = deliveryMapper.markFailed(deliveryId, DeliveryStatus.FAILED, 1,
            "IllegalStateException", "broker down", LocalDateTime.now().plusMinutes(2),
            DeliveryStatus.REQUESTED);

        assertThat(updated).isZero();
        assertThat(deliveryMapper.findById(deliveryId).orElseThrow().getStatus())
            .isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void futureRetryTimeIsNotPickedUpYet() {
        Long deliveryId = givenDelivery(givenNotification(), LocalDateTime.now().plusMinutes(5));

        assertThat(deliveryMapper.findRetryTargets(3, 100))
            .extracting(NotificationDeliveryRetryRow::deliveryId)
            .doesNotContain(deliveryId);
    }

    private Long givenNotification() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "delivery-" + suffix + "@test.local";
        jdbcTemplate.update("""
            INSERT INTO members (email, password, nickname, phone)
            VALUES (?, ?, ?, ?)
            """, email, "{noop}test", "전달테스트-" + suffix, "010-1234-5678");
        Long memberId = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);

        Notification notification = new Notification();
        notification.setReceiverId(memberId);
        notification.setNotificationType(NotificationType.ORDER_PAID);
        notification.setTitle("결제 완료");
        notification.setContent("결제가 완료되었습니다.");
        notification.setTargetUrl("/orders/1");
        notificationMapper.insert(notification);
        return notification.getId();
    }

    private Long givenDelivery(Long notificationId, LocalDateTime nextRetryAt) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotificationId(notificationId);
        delivery.setChannel(DeliveryChannel.WEBSOCKET);
        delivery.setStatus(DeliveryStatus.REQUESTED);
        delivery.setNextRetryAt(nextRetryAt);
        deliveryMapper.insert(delivery);
        return delivery.getId();
    }
}
