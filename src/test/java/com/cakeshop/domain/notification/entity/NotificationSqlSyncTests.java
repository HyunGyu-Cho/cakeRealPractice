package com.cakeshop.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 값을 추가한 쪽 업무(결제·채팅)가 런타임에 실패한다.
 * SQL 을 수동 적용하는 프로젝트라 이 어긋남을 CI 에서 잡을 곳이 여기뿐이다.
 * 증분 V파일과 보관용 정본(V0_ERD.sql)을 함께 검증해 소급 반영 누락도 잡는다.
 */
class NotificationSqlSyncTests {

    private static final Path SQL_DIR = Path.of("docs/sql");
    private static final Path V0_ERD = SQL_DIR.resolve("V0_ERD.sql");

    @Test
    void notificationTypeMatchesCheckConstraint() {
        Set<String> enumNames = names(NotificationType.values());

        assertThat(latestIncrementalValues("chk_notifications_type"))
            .as("증분 V파일의 마지막 chk_notifications_type 정의")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_notifications_type"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void deliveryStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(DeliveryStatus.values());

        assertThat(latestIncrementalValues("chk_notification_deliveries_status"))
            .as("증분 V파일의 마지막 chk_notification_deliveries_status 정의")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_notification_deliveries_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void deliveryChannelMatchesCheckConstraint() {
        Set<String> enumNames = names(DeliveryChannel.values());

        assertThat(latestIncrementalValues("chk_notification_deliveries_channel"))
            .as("증분 V파일의 마지막 chk_notification_deliveries_channel 정의")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_notification_deliveries_channel"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    /**
     * 증분 V파일(V2+)을 번호 순으로 훑어 제약을 마지막으로 정의한 파일의 값을 돌려준다.
     * 파일 번호를 테스트에 박아 두면 나중에 값을 늘리는 V파일이 생겨도 옛 파일만 보게 된다
     * (실제로 V19에서 그랬다) — 증분의 마지막 정의가 곧 DB의 현재 상태다.
     */
    private List<String> latestIncrementalValues(String constraintName) {
        List<Path> files = incrementalFilesInOrder();
        Path latest = null;
        for (Path file : files) {
            if (read(file).contains(constraintName)) {
                latest = file;
            }
        }
        assertThat(latest)
            .as("증분 V파일 어디에도 %s 제약이 없습니다.", constraintName)
            .isNotNull();
        return checkValues(latest, constraintName);
    }

    private List<Path> incrementalFilesInOrder() {
        try (Stream<Path> paths = Files.list(SQL_DIR)) {
            Pattern numbered = Pattern.compile("^V(\\d+)_.*\\.sql$");
            return paths
                .filter(path -> numbered.matcher(path.getFileName().toString()).matches())
                .map(path -> Map.entry(versionOf(path, numbered), path))
                .filter(entry -> entry.getKey() >= 2)   // V0·V1은 보관용 정본이라 증분이 아니다
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int versionOf(Path path, Pattern numbered) {
        Matcher matcher = numbered.matcher(path.getFileName().toString());
        matcher.matches();
        return Integer.parseInt(matcher.group(1));
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
