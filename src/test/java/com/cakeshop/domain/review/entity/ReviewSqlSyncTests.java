package com.cakeshop.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.support.MigrationSql;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 후기 저장·숨김이 런타임에 실패한다.
 * 마이그레이션 SQL은 자바 컴파일러가 보지 않으므로 이 어긋남을 잡을 곳이 여기뿐이다.
 */
class ReviewSqlSyncTests {

    @Test
    void reviewStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_reviews_status"))
            .containsExactlyInAnyOrderElementsOf(names(ReviewStatus.values()));
    }

    /** 삭제를 상태로 두지 않기로 한 결정이 코드에서 되살아나지 않게 못을 박는다. */
    @Test
    void deletedIsNotAReviewStatus() {
        assertThat(names(ReviewStatus.values())).containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
    }

    /** 세부 3축은 선택이라 NULL 허용이어야 한다. NOT NULL로 되돌아가면 작성 폼이 깨진다. */
    @Test
    void detailRatingsAreNullable() {
        String sql = MigrationSql.allText();
        for (String column : List.of("taste_rating", "design_rating", "service_rating")) {
            assertThat(sql)
                .as("%s 는 NULL 허용이어야 한다", column)
                .containsPattern("`" + column + "`\\s+TINYINT UNSIGNED NULL");
        }
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
