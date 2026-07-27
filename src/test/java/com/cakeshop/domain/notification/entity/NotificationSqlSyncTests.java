package com.cakeshop.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.support.MigrationSql;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 값을 추가한 쪽 업무(결제·채팅)가 런타임에 실패한다.
 * 마이그레이션 SQL은 자바 컴파일러가 보지 않으므로 이 어긋남을 CI에서 잡을 곳이 여기뿐이다.
 *
 * <p>제약을 마지막으로 정의한 마이그레이션을 기준으로 본다(V19에서 REVIEW_REPLY가 늘어난 것처럼
 * 값은 나중에 추가될 수 있다). 자세한 규칙은 {@link MigrationSql}.
 */
class NotificationSqlSyncTests {

    @Test
    void notificationTypeMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_notifications_type"))
            .containsExactlyInAnyOrderElementsOf(names(NotificationType.values()));
    }

    @Test
    void deliveryStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_notification_deliveries_status"))
            .containsExactlyInAnyOrderElementsOf(names(DeliveryStatus.values()));
    }

    @Test
    void deliveryChannelMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_notification_deliveries_channel"))
            .containsExactlyInAnyOrderElementsOf(names(DeliveryChannel.values()));
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
