package com.cakeshop.domain.review.entity;

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
 * enum 값과 DDL CHECK 목록이 어긋나면 후기 저장·숨김이 런타임에 실패한다.
 * SQL을 수동 적용하는 프로젝트라 이 어긋남을 잡을 곳이 여기뿐이다.
 */
class ReviewSqlSyncTests {

    private static final Path V0_ERD = Path.of("docs/sql/V0_ERD.sql");
    private static final Path V16_REVIEW = Path.of("docs/sql/V16_review.sql");

    @Test
    void reviewStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(ReviewStatus.values());

        assertThat(checkValues(V16_REVIEW, "chk_reviews_status"))
            .as("V16 의 chk_reviews_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_reviews_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    /** 삭제를 상태로 두지 않기로 한 결정이 코드에서 되살아나지 않게 못을 박는다. */
    @Test
    void deletedIsNotAReviewStatus() {
        assertThat(names(ReviewStatus.values())).containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
    }

    /** 세부 3축은 선택이라 NULL 허용이어야 한다. NOT NULL로 되돌아가면 작성 폼이 깨진다. */
    @Test
    void detailRatingsAreNullableInBothSqlFiles() {
        for (Path path : List.of(V16_REVIEW, V0_ERD)) {
            String sql = read(path);
            for (String column : List.of("taste_rating", "design_rating", "service_rating")) {
                assertThat(sql)
                    .as("%s 의 %s 는 NULL 허용이어야 한다", path, column)
                    .containsPattern("`" + column + "`\\s+TINYINT UNSIGNED NULL");
            }
        }
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
