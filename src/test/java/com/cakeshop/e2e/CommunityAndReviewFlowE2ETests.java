package com.cakeshop.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 커뮤니티(글·댓글·신고·제재)와 후기(작성·수정·삭제·관리자 숨김·답글) 시나리오를
 * 고객과 관리자 <b>두 역할이 번갈아 조작하는 흐름</b>으로 검증한다.
 *
 * <p>{@code @Transactional} 이므로 삽입한 회원·게시글·주문은 테스트 종료 시 롤백된다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CommunityAndReviewFlowE2ETests {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ─────────────────────────── 커뮤니티 ───────────────────────────

    @Test
    void 글_작성부터_댓글_신고_관리자_제재까지_이어진다() throws Exception {
        String suffix = shortId();
        MemberDetails writer = givenCustomer("작성자-" + suffix);
        MemberDetails reporter = givenCustomer("신고자-" + suffix);
        MemberDetails admin = givenAdmin();
        String categoryCode = jdbcTemplate.queryForObject(
            "SELECT code FROM post_categories WHERE is_active = 1 ORDER BY sort_order LIMIT 1",
            String.class);

        // 1. 작성 화면 → 등록 → 상세로 리다이렉트
        mockMvc.perform(get("/community/new").with(user(writer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/community/form"))
            .andExpect(model().attributeExists("categories", "form"));

        String redirect = mockMvc.perform(post("/community").with(user(writer)).with(csrf())
                .param("categoryCode", categoryCode)
                .param("title", "E2E 게시글-" + suffix)
                .param("content", "E2E 본문 내용입니다."))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "게시글이 등록되었습니다."))
            .andReturn().getResponse().getRedirectedUrl();
        long postId = Long.parseLong(redirect.substring(redirect.lastIndexOf('/') + 1));

        // 2. 비로그인도 상세를 볼 수 있고 조회수가 오른다
        mockMvc.perform(get("/community/{id}", postId))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/community/detail"))
            .andExpect(model().attribute("loggedIn", false));
        assertThat(viewCount(postId)).isPositive();

        // 3. 수정
        mockMvc.perform(post("/community/{id}/edit", postId).with(user(writer)).with(csrf())
                .param("categoryCode", categoryCode)
                .param("title", "E2E 수정본-" + suffix)
                .param("content", "수정된 본문입니다."))
            .andExpect(redirectedUrl("/community/" + postId))
            .andExpect(flash().attribute("successMessage", "게시글이 수정되었습니다."));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT title FROM posts WHERE id = ?", String.class, postId))
            .isEqualTo("E2E 수정본-" + suffix);

        // 4. 다른 회원이 댓글 작성
        mockMvc.perform(post("/community/{id}/comments", postId).with(user(reporter)).with(csrf())
                .param("content", "E2E 댓글입니다."))
            .andExpect(redirectedUrl("/community/" + postId))
            .andExpect(flash().attribute("successMessage", "댓글이 등록되었습니다."));
        Long commentId = jdbcTemplate.queryForObject(
            "SELECT id FROM comments WHERE post_id = ? ORDER BY id DESC LIMIT 1", Long.class, postId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM comments WHERE id = ?", String.class, commentId)).isEqualTo("ACTIVE");

        // 5. 좋아요 토글 (JSON API — JS가 호출하는 엔드포인트를 서버 측에서 직접 검증)
        mockMvc.perform(post("/community/api/posts/{id}/like", postId)
                .with(user(reporter)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(likeCount(postId)).isEqualTo(1);
        mockMvc.perform(post("/community/api/posts/{id}/like", postId)
                .with(user(reporter)).with(csrf()))
            .andExpect(status().isOk());
        assertThat(likeCount(postId)).isZero();

        // 6. 신고 — 접수되고, 같은 회원의 중복 신고는 거부된다
        mockMvc.perform(post("/community/{id}/report", postId).with(user(reporter)).with(csrf())
                .param("reason", "E2E 신고 사유"))
            .andExpect(redirectedUrl("/community/" + postId))
            .andExpect(flash().attribute("successMessage", "신고가 접수되었습니다."));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM post_reports WHERE post_id = ?", String.class, postId))
            .isEqualTo("PENDING");

        mockMvc.perform(post("/community/{id}/report", postId).with(user(reporter)).with(csrf())
                .param("reason", "중복 신고"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM post_reports WHERE post_id = ?", Integer.class, postId))
            .isEqualTo(1);

        // 7. 관리자가 목록·상세에서 확인하고 제재
        mockMvc.perform(get("/admin/community").with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/community/list"))
            .andExpect(model().attributeExists("pageResult"));
        mockMvc.perform(get("/admin/community/{id}", postId).with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/community/detail"))
            .andExpect(model().attributeExists("post", "blockForm"));

        mockMvc.perform(post("/admin/community/{id}/block", postId).with(user(admin)).with(csrf())
                .param("reason", "E2E 제재 사유"))
            .andExpect(redirectedUrl("/admin/community/" + postId))
            .andExpect(flash().attribute("successMessage", "게시글을 제재했습니다."));
        assertThat(postStatus(postId)).isEqualTo("BLOCKED");

        // 8. 제재 해제 후 작성자가 삭제 — 상태는 DELETED 로 남는다(하드 삭제 아님)
        mockMvc.perform(post("/admin/community/{id}/unblock", postId).with(user(admin)).with(csrf()))
            .andExpect(redirectedUrl("/admin/community/" + postId));
        assertThat(postStatus(postId)).isEqualTo("ACTIVE");

        mockMvc.perform(post("/community/{id}/delete", postId).with(user(writer)).with(csrf()))
            .andExpect(redirectedUrl("/community"))
            .andExpect(flash().attribute("successMessage", "게시글이 삭제되었습니다."));
        assertThat(postStatus(postId)).isEqualTo("DELETED");
    }

    /** 남의 글은 수정·삭제할 수 없다. */
    @Test
    void 작성자가_아니면_글을_수정하거나_삭제할_수_없다() throws Exception {
        String suffix = shortId();
        MemberDetails writer = givenCustomer("주인-" + suffix);
        MemberDetails stranger = givenCustomer("남-" + suffix);
        long postId = givenPost(writer.getMemberId(), suffix);

        mockMvc.perform(post("/community/{id}/delete", postId).with(user(stranger)).with(csrf()))
            .andExpect(status().is4xxClientError());
        assertThat(postStatus(postId)).isEqualTo("ACTIVE");
    }

    /** 무한스크롤이 쓰는 JSON 목록도 공개 조회다. */
    @Test
    void 무한스크롤_목록_API는_비로그인으로_조회된다() throws Exception {
        mockMvc.perform(get("/community/scroll"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/community/list-scroll"));
        mockMvc.perform(get("/community/api/posts"))
            .andExpect(status().isOk());
    }

    // ─────────────────────────── 후기 ───────────────────────────

    @Test
    void 픽업완료_주문에_후기를_쓰고_관리자가_답글과_숨김을_처리한다() throws Exception {
        String suffix = shortId();
        MemberDetails customer = givenCustomer("후기-" + suffix);
        MemberDetails admin = givenAdmin();
        Long orderItemId = givenPickedUpOrderItem(customer.getMemberId(), suffix);

        // 1. 내 후기함에 작성 가능 항목으로 잡힌다
        mockMvc.perform(get("/reviews").with(user(customer)))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/list"))
            .andExpect(model().attributeExists("reviewableItems", "myReviews"));

        mockMvc.perform(get("/reviews/new").with(user(customer)).param("orderItemId", orderItemId.toString()))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeExists("target", "reviewForm"));

        // 2. 작성 — 종합 평점만 필수, 세부 축은 비워둘 수 있다
        mockMvc.perform(post("/reviews").with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "5")
                .param("content", "정말 맛있었습니다. E2E 후기 본문."))
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attribute("successMessage", "후기를 등록했습니다."));

        Long reviewId = jdbcTemplate.queryForObject(
            "SELECT id FROM reviews WHERE order_item_id = ?", Long.class, orderItemId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM reviews WHERE id = ?", String.class, reviewId)).isEqualTo("VISIBLE");

        // 3. 같은 주문 상품에 두 번 쓰지 못한다
        mockMvc.perform(post("/reviews").with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "4")
                .param("content", "중복 작성 시도입니다."))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeHasErrors("reviewForm"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", Integer.class, orderItemId))
            .isEqualTo(1);

        // 4. 내용이 10자 미만이면 검증 실패로 폼을 재렌더한다(리다이렉트하지 않는다)
        mockMvc.perform(post("/reviews/{id}/edit", reviewId).with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "4")
                .param("content", "짧다"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/review/form"))
            .andExpect(model().attributeHasFieldErrors("reviewForm", "content"));

        // 5. 정상 수정
        mockMvc.perform(post("/reviews/{id}/edit", reviewId).with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "3")
                .param("tasteRating", "4")
                .param("content", "다시 생각해보니 보통이었습니다."))
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attribute("successMessage", "후기를 수정했습니다."));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT overall_rating FROM reviews WHERE id = ?", Integer.class, reviewId)).isEqualTo(3);

        // 6. 관리자가 답글을 달고 숨긴다
        mockMvc.perform(get("/admin/reviews").with(user(admin)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/review/list"))
            .andExpect(model().attributeExists("pageResult", "reviewStatuses", "replyForm"));

        mockMvc.perform(post("/admin/reviews/{id}/reply", reviewId).with(user(admin)).with(csrf())
                .param("content", "소중한 의견 감사합니다."))
            .andExpect(redirectedUrl("/admin/reviews"))
            .andExpect(flash().attribute("successMessage", "답글을 저장했습니다."));

        // 답글 등록은 작성자에게 알림을 남긴다(V19 REVIEW_REPLY)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE receiver_id = ? AND notification_type = 'REVIEW_REPLY'",
            Integer.class, customer.getMemberId())).isEqualTo(1);

        // 답글 수정은 재발행하지 않는다 — 같은 답글로 작성자를 반복해서 깨우지 않는다
        mockMvc.perform(post("/admin/reviews/{id}/reply", reviewId).with(user(admin)).with(csrf())
                .param("content", "답글을 수정합니다. 감사합니다."))
            .andExpect(redirectedUrl("/admin/reviews"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE receiver_id = ? AND notification_type = 'REVIEW_REPLY'",
            Integer.class, customer.getMemberId())).isEqualTo(1);

        mockMvc.perform(post("/admin/reviews/{id}/status", reviewId).with(user(admin)).with(csrf())
                .param("status", "HIDDEN"))
            .andExpect(redirectedUrl("/admin/reviews"))
            .andExpect(flash().attribute("successMessage", "후기를 숨겼습니다."));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM reviews WHERE id = ?", String.class, reviewId)).isEqualTo("HIDDEN");

        // 7. 삭제하면 같은 주문 상품에 다시 쓸 수 있다
        mockMvc.perform(post("/reviews/{id}/delete", reviewId).with(user(customer)).with(csrf()))
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attribute("successMessage",
                "후기를 삭제했습니다. 같은 주문 상품에 다시 작성할 수 있습니다."));

        mockMvc.perform(post("/reviews").with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "5")
                .param("content", "삭제 후 다시 작성한 후기입니다."))
            .andExpect(redirectedUrl("/reviews"))
            .andExpect(flash().attribute("successMessage", "후기를 등록했습니다."));
    }

    /** 픽업하지 않은 주문에는 후기를 쓸 수 없다 — 작성 자격은 서버가 판정한다. */
    @Test
    void 픽업하지_않은_주문에는_후기를_쓸_수_없다() throws Exception {
        String suffix = shortId();
        MemberDetails customer = givenCustomer("미픽업-" + suffix);
        Long orderItemId = givenOrderItem(customer.getMemberId(), suffix, "PAID");

        mockMvc.perform(get("/reviews/new").with(user(customer))
                .param("orderItemId", orderItemId.toString()))
            .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/reviews").with(user(customer)).with(csrf())
                .param("orderItemId", orderItemId.toString())
                .param("overallRating", "5")
                .param("content", "픽업 전에 쓰는 후기입니다."))
            .andExpect(status().is4xxClientError());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", Integer.class, orderItemId))
            .isZero();
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private MemberDetails givenCustomer(String nickname) {
        String email = "e2e-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("""
            INSERT INTO members (email, password, nickname, phone, role, status)
            VALUES (?, '{noop}x', ?, '010-0000-0000', 'USER', 'ACTIVE')
            """, email, nickname);
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?", Long.class, email);
        return new MemberDetails(id, email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MemberDetails givenAdmin() {
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE role = 'ADMIN' ORDER BY id LIMIT 1", Long.class);
        return new MemberDetails(id, "admin@cakeshop.local", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private long givenPost(Long memberId, String suffix) {
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM post_categories ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update("""
            INSERT INTO posts (member_id, category_id, title, content, status)
            VALUES (?, ?, ?, '본문', 'ACTIVE')
            """, memberId, categoryId, "픽스처글-" + suffix);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM posts WHERE title = ?", Long.class, "픽스처글-" + suffix);
    }

    private Long givenPickedUpOrderItem(Long memberId, String suffix) {
        return givenOrderItem(memberId, suffix, "PICKED_UP");
    }

    /** 후기 자격 판정에 필요한 최소 주문 한 건을 직접 만든다(결제 흐름은 별도 테스트가 검증한다). */
    private Long givenOrderItem(Long memberId, String suffix, String orderStatus) {
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        String productName = "후기상품-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO products
                (category_id, name, base_price, product_type, preparation_days,
                 cancellation_limit_days, stock_quantity, status)
            VALUES (?, ?, 30000, 'GENERAL', 0, 0, 10, 'ACTIVE')
            """, categoryId, productName);
        Long productId = jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = ?", Long.class, productName);

        String orderNumber = "E2E-" + suffix + "-" + shortId();
        jdbcTemplate.update("""
            INSERT INTO orders
                (order_number, member_id, orderer_name, orderer_phone, pickup_name, pickup_phone,
                 original_amount, discount_amount, final_amount, status, pickup_at, picked_up_at)
            VALUES (?, ?, '주문자', '010-1111-2222', '픽업자', '010-3333-4444',
                    30000, 0, 30000, ?, ?, ?)
            """, orderNumber, memberId, orderStatus,
            LocalDateTime.now().minusDays(1),
            "PICKED_UP".equals(orderStatus) ? LocalDateTime.now().minusHours(1) : null);
        Long orderId = jdbcTemplate.queryForObject(
            "SELECT id FROM orders WHERE order_number = ?", Long.class, orderNumber);

        jdbcTemplate.update("""
            INSERT INTO order_items
                (order_id, product_id, product_name, product_type, quantity,
                 base_price, option_amount, total_amount)
            VALUES (?, ?, ?, 'GENERAL', 1, 30000, 0, 30000)
            """, orderId, productId, productName);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }

    private String postStatus(long postId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private long viewCount(long postId) {
        return jdbcTemplate.queryForObject(
            "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long likeCount(long postId) {
        return jdbcTemplate.queryForObject(
            "SELECT like_count FROM posts WHERE id = ?", Long.class, postId);
    }
}
