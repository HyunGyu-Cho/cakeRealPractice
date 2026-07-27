package com.cakeshop.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.support.MigrationSql;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * enum 값과 DDL CHECK 목록이 어긋나면 결제·발급이 런타임에 실패한다.
 * 마이그레이션 SQL은 자바 컴파일러가 보지 않으므로 이 어긋남을 잡을 곳이 여기뿐이다.
 */
class CouponSqlSyncTests {

    @Test
    void couponStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_coupons_status"))
            .containsExactlyInAnyOrderElementsOf(names(CouponStatus.values()));
    }

    @Test
    void memberCouponStatusMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_member_coupons_status"))
            .containsExactlyInAnyOrderElementsOf(names(MemberCouponStatus.values()));
    }

    @Test
    void discountTypeMatchesCheckConstraint() {
        assertThat(MigrationSql.checkValues("chk_coupons_discount_type"))
            .containsExactlyInAnyOrderElementsOf(names(DiscountType.values()));
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
