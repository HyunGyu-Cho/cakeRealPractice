package com.cakeshop.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.support.MigrationSql;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 값을 추가한 쪽이 런타임에 실패한다.
 * 마이그레이션 SQL은 자바 컴파일러가 보지 않으므로 이 어긋남을 CI에서 잡을 곳이 여기뿐이다.
 */
class PaymentSqlSyncTests {

    @Test
    void paymentStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_payments_status"))
            .containsExactlyInAnyOrderElementsOf(names(PaymentStatus.values()));
    }

    @Test
    void paymentCancellationStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_payment_cancellations_status"))
            .containsExactlyInAnyOrderElementsOf(names(PaymentCancellationStatus.values()));
    }

    @Test
    void webhookProcessStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_webhook_events_process_status"))
            .containsExactlyInAnyOrderElementsOf(names(WebhookProcessStatus.values()));
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
