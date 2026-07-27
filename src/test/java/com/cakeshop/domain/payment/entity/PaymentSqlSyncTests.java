package com.cakeshop.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 값을 추가한 쪽이 런타임에 실패한다.
 * SQL 을 수동 적용하는 프로젝트라 이 어긋남을 CI 에서 잡을 곳이 여기뿐이다
 * (notification 패턴과 동일). 증분 V파일과 보관용 정본을 함께 검증해 소급 반영 누락도 잡는다.
 */
class PaymentSqlSyncTests {

    private static final Path V0_ERD = Path.of("docs/sql/V0_ERD.sql");
    private static final Path V1_MVP = Path.of("docs/sql/V1_first_MVC_table.sql");
    private static final Path V17_TOSS = Path.of("docs/sql/V17_toss_payment.sql");

    @Test
    void paymentStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(PaymentStatus.values());

        assertThat(checkValues(V1_MVP, "chk_payments_status"))
            .as("V1 의 chk_payments_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_payments_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void paymentCancellationStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(PaymentCancellationStatus.values());

        assertThat(checkValues(V1_MVP, "chk_payment_cancellations_status"))
            .as("V1 의 chk_payment_cancellations_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_payment_cancellations_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void webhookProcessStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(WebhookProcessStatus.values());

        assertThat(checkValues(V17_TOSS, "chk_webhook_events_process_status"))
            .as("V17 의 chk_webhook_events_process_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_webhook_events_process_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    /** 지정한 제약의 마지막 정의에서 {@code IN (...)} 안의 값을 뽑는다(재정의된 경우 최신 것). */
    private List<String> checkValues(Path sqlPath, String constraintName) {
        String sql = read(sqlPath);
        Matcher constraint = Pattern.compile(
            Pattern.quote(constraintName) + "`?\\s*\\r?\\n?\\s*CHECK\\s*\\([^)]*IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE).matcher(sql);

        String values = null;
        while (constraint.find()) {
            values = constraint.group(1);
        }
        assertThat(values)
            .as("%s 에서 %s 제약을 찾지 못했습니다.", sqlPath, constraintName)
            .isNotNull();

        return Arrays.stream(values.split(","))
            .map(value -> value.trim().replace("'", ""))
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
