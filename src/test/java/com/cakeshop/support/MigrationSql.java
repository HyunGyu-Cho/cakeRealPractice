package com.cakeshop.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Flyway 마이그레이션 SQL을 읽어 "현재 스키마가 어떤 상태인가"를 알려주는 테스트 도우미.
 *
 * <p>enum 값과 DDL의 {@code CHECK} 목록이 어긋나면 값을 추가한 쪽이 런타임에 실패한다.
 * 이 어긋남을 CI에서 잡을 곳이 각 도메인의 {@code *SqlSyncTests}뿐이라 공통 로직을 여기 모은다.
 *
 * <p><b>왜 마지막 정의를 쓰나</b> — 제약은 나중 마이그레이션이 다시 정의할 수 있다(값이 늘어날 때).
 * 파일 번호를 테스트에 박아 두면 값을 늘리는 새 마이그레이션이 생겨도 옛 파일만 보게 된다.
 * 번호 순으로 훑어 <b>마지막으로 정의한 파일</b>의 값이 곧 DB의 현재 상태다.
 *
 * <p>전환 이전에는 증분 V파일과 보관용 정본(V0_ERD.sql)을 함께 검증해 "소급 반영 누락"도 잡았다.
 * Flyway 도입으로 정본 이중 관리가 사라져 확인할 곳이 마이그레이션 한 줄기뿐이다.
 */
public final class MigrationSql {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Pattern FILE_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private MigrationSql() {
    }

    /**
     * 지정한 제약을 마지막으로 정의한 마이그레이션에서 {@code IN (...)} 안의 값을 뽑는다.
     *
     * @param constraintName 예: {@code chk_payments_status}
     */
    public static List<String> checkValues(String constraintName) {
        Path latest = null;
        for (Path file : filesInVersionOrder()) {
            if (read(file).contains(constraintName)) {
                latest = file;
            }
        }
        assertThat(latest)
            .as("마이그레이션 어디에도 %s 제약이 없습니다.", constraintName)
            .isNotNull();

        Matcher constraint = Pattern.compile(
            Pattern.quote(constraintName) + "`?\\s*\\r?\\n?\\s*CHECK\\s*\\([^)]*IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE).matcher(read(latest));

        String values = null;
        while (constraint.find()) {
            values = constraint.group(1);
        }
        assertThat(values)
            .as("%s 에서 %s 제약의 IN 목록을 찾지 못했습니다.", latest, constraintName)
            .isNotNull();

        return Arrays.stream(values.split(","))
            .map(value -> value.trim().replace("'", ""))
            .filter(value -> !value.isEmpty())
            .toList();
    }

    /**
     * 전체 마이그레이션을 버전 순으로 이어 붙인 텍스트.
     *
     * <p>컬럼 정의처럼 제약명으로 집어내기 어려운 것을 정규식으로 확인할 때 쓴다.
     * 이어 붙인 텍스트라 "어딘가에 이런 정의가 있다"까지만 보증한다 —
     * 뒤 마이그레이션이 그 정의를 다시 바꿨는지는 알려주지 않는다.
     */
    public static String allText() {
        StringBuilder sb = new StringBuilder();
        for (Path file : filesInVersionOrder()) {
            sb.append(read(file)).append('\n');
        }
        return sb.toString();
    }

    /** 베이스라인(V1)을 포함한 전체 마이그레이션을 버전 번호 순으로 돌려준다. */
    private static List<Path> filesInVersionOrder() {
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            List<Path> files = new ArrayList<>(paths
                .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                .toList());
            // 파일명 문자열로 정렬하면 V10이 V2보다 앞선다. 번호를 숫자로 비교해야 한다.
            files.sort(Comparator.comparingInt(MigrationSql::version));
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int version(Path path) {
        Matcher matcher = FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalStateException("마이그레이션 파일명 규칙에 맞지 않습니다: " + path);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
