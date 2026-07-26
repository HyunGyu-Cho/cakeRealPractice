package com.cakeshop.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.entity.ProductType;
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
 * enum 값과 DDL CHECK 목록이 어긋나면 런타임에 INSERT/UPDATE가 실패한다.
 * SQL 을 수동 적용하는 프로젝트라 이 어긋남을 CI 에서 잡을 곳이 여기뿐이다.
 * 증분 V파일과 보관용 정본(V0_ERD.sql)을 함께 검증해 소급 반영 누락도 잡는다.
 */
class CustomOrderSqlSyncTests {

    private static final Path V0_ERD = Path.of("docs/sql/V0_ERD.sql");
    private static final Path V13_CUSTOM_ORDER = Path.of("docs/sql/V13_custom_order.sql");
    private static final Path V14_PRODUCT_TYPE = Path.of("docs/sql/V14_product_type_general.sql");

    @Test
    void quoteStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(QuoteStatus.values());

        assertThat(checkValues(V13_CUSTOM_ORDER, "chk_custom_order_quotes_status"))
            .as("V13 의 chk_custom_order_quotes_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_custom_order_quotes_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void paymentLinkStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(PaymentLinkStatus.values());

        assertThat(checkValues(V13_CUSTOM_ORDER, "chk_custom_order_payment_links_status"))
            .as("V13 의 chk_custom_order_payment_links_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_custom_order_payment_links_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void productOptionStatusMatchesCheckConstraint() {
        Set<String> enumNames = names(ProductOptionStatus.values());

        assertThat(checkValues(V13_CUSTOM_ORDER, "chk_product_options_status"))
            .as("V13 의 chk_product_options_status")
            .containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(checkValues(V0_ERD, "chk_product_options_status"))
            .as("V0_ERD 소급 반영")
            .containsExactlyInAnyOrderElementsOf(enumNames);
    }

    @Test
    void productTypeMatchesCheckConstraint() {
        assertThat(checkValues(V14_PRODUCT_TYPE, "chk_products_type"))
            .as("V14 의 chk_products_type")
            .containsExactlyInAnyOrderElementsOf(names(ProductType.values()));
    }

    /** 주문 상태는 팀 확정값이라 이번 작업에서 바뀌지 않았음을 못박아 둔다. */
    @Test
    void orderStatusStaysAtTeamAgreedSevenValues() {
        assertThat(names(OrderStatus.values())).containsExactlyInAnyOrder(
            "UNDER_REVIEW", "IN_PRODUCTION", "REJECTED",
            "PAID", "READY_FOR_PICKUP", "PICKED_UP", "CANCELED");
        assertThat(checkValues(V0_ERD, "chk_orders_status"))
            .containsExactlyInAnyOrderElementsOf(names(OrderStatus.values()));
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
