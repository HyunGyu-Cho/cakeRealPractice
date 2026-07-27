package com.cakeshop.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.support.MigrationSql;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 런타임에 INSERT/UPDATE가 실패한다.
 * 마이그레이션 SQL은 자바 컴파일러가 보지 않으므로 이 어긋남을 CI에서 잡을 곳이 여기뿐이다.
 */
class CustomOrderSqlSyncTests {

    @Test
    void quoteStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_custom_order_quotes_status"))
            .containsExactlyInAnyOrderElementsOf(names(QuoteStatus.values()));
    }

    @Test
    void paymentLinkStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_custom_order_payment_links_status"))
            .containsExactlyInAnyOrderElementsOf(names(PaymentLinkStatus.values()));
    }

    @Test
    void productOptionStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_product_options_status"))
            .containsExactlyInAnyOrderElementsOf(names(ProductOptionStatus.values()));
    }

    @Test
    void productTypeMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_products_type"))
            .containsExactlyInAnyOrderElementsOf(names(ProductType.values()));
    }

    /** 주문 상태는 팀 확정값이라 함부로 늘어나지 않았음을 못박아 둔다. */
    @Test
    void orderStatusStaysAtTeamAgreedSevenValues() {
        assertThat(names(OrderStatus.values())).containsExactlyInAnyOrder(
            "UNDER_REVIEW", "IN_PRODUCTION", "REJECTED",
            "PAID", "READY_FOR_PICKUP", "PICKED_UP", "CANCELED");
        assertThat(MigrationSql.checkValues("chk_orders_status"))
            .containsExactlyInAnyOrderElementsOf(names(OrderStatus.values()));
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
