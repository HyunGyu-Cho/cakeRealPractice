package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.stats.MemberCountRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 관리 SQL 과 회원별 건수 집계 계약을 실제 MariaDB 에 대고 검증한다.
 * 서비스 단위 테스트는 mapper 를 mock 하므로 컬럼·record 매핑 오류가 드러나지 않는다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class MemberAdminMapperTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MemberMapper memberMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired ReviewMapper reviewMapper;
    @Autowired CommunityMapper communityMapper;

    private Long givenMember(MemberStatus status) {
        String email = "admin-test-" + UUID.randomUUID() + "@cakeshop.local";
        jdbcTemplate.update("""
            INSERT INTO members (email, password, nickname, phone, role, status)
            VALUES (?, 'x', '검색용회원', '010-0000-0000', 'USER', ?)
            """, email, status.name());
        return jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    @Test
    void 검색_조건은_이름_이메일_상태로_좁혀진다() {
        Long suspended = givenMember(MemberStatus.SUSPENDED);

        AdminMemberSearchForm cond = new AdminMemberSearchForm();
        cond.setName("검색용회원");
        cond.setStatus("SUSPENDED");

        long total = memberMapper.countAdminMembers(cond);
        List<Member> page = memberMapper.findAdminMemberPage(cond, 10, 0);

        assertThat(total).isPositive();
        assertThat(page).extracting(Member::getId).contains(suspended);
        assertThat(page).allSatisfy(member ->
            assertThat(member.getStatus()).isEqualTo("SUSPENDED"));

        // 조건이 비면 전체 조회다.
        AdminMemberSearchForm empty = new AdminMemberSearchForm();
        assertThat(memberMapper.countAdminMembers(empty)).isGreaterThanOrEqualTo(total);
    }

    @Test
    void 제재는_ACTIVE일_때만_해제는_SUSPENDED일_때만_반영된다() {
        Long memberId = givenMember(MemberStatus.ACTIVE);

        assertThat(memberMapper.suspend(memberId, "약관 위반")).isEqualTo(1);
        Member suspended = memberMapper.findById(memberId).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo("SUSPENDED");
        assertThat(suspended.getSuspendedReason()).isEqualTo("약관 위반");
        assertThat(suspended.getSuspendedAt()).isNotNull();

        // 이미 제재된 회원에게 다시 걸면 0행 — 서비스가 이 값을 실패로 읽는다.
        assertThat(memberMapper.suspend(memberId, "중복")).isZero();

        assertThat(memberMapper.unsuspend(memberId)).isEqualTo(1);
        Member released = memberMapper.findById(memberId).orElseThrow();
        assertThat(released.getStatus()).isEqualTo("ACTIVE");
        assertThat(released.getSuspendedAt()).isNull();
        assertThat(released.getSuspendedReason()).isNull();

        assertThat(memberMapper.unsuspend(memberId)).isZero();
    }

    /** 세 도메인의 회원별 건수 집계가 실제로 실행되고 record 로 매핑되는지 확인한다. */
    @Test
    void 회원별_건수_집계는_비어_있어도_실행된다() {
        Long memberId = givenMember(MemberStatus.ACTIVE);
        List<Long> ids = List.of(memberId);

        assertThat(orderMapper.countOrdersByMemberIds(ids)).isEmpty();
        assertThat(reviewMapper.countReviewsByMemberIds(ids)).isEmpty();
        assertThat(communityMapper.countActivePostsByMemberIds(ids)).isEmpty();
    }

    @Test
    void 작성한_글은_회원별_집계에_잡힌다() {
        Long memberId = givenMember(MemberStatus.ACTIVE);
        Optional<Long> categoryId = Optional.ofNullable(jdbcTemplate.query(
            "SELECT id FROM post_categories ORDER BY id LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null));
        if (categoryId.isEmpty()) {
            return; // post_categories 시드가 없는 DB에서는 검증을 건너뛴다.
        }
        jdbcTemplate.update("""
            INSERT INTO posts (member_id, category_id, title, content, status)
            VALUES (?, ?, '집계 확인용', '내용', 'ACTIVE')
            """, memberId, categoryId.get());

        List<MemberCountRow> rows = communityMapper.countActivePostsByMemberIds(List.of(memberId));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.memberId()).isEqualTo(memberId);
            assertThat(row.count()).isEqualTo(1L);
        });
    }
}
