package com.cakeshop.domain.community.mapper;

import com.cakeshop.domain.community.dto.view.AdminPostDetailRow;
import com.cakeshop.domain.community.dto.view.AdminPostSummaryRow;
import com.cakeshop.domain.community.dto.view.CommentRow;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostSummaryRow;
import com.cakeshop.domain.community.dto.view.ReportRow;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.global.common.stats.MemberCountRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityMapper {

    // 화면 선택지용 — 필터·폼이 쓰는 code·name만 내려준다.
    List<PostCategoryView> findActiveCategories();

    long countActivePosts(@Param("categoryCode") String categoryCode);

    // 페이지 번호 방식: LIMIT #{size} OFFSET #{offset}
    List<PostSummaryRow> findActivePostPage(@Param("categoryCode") String categoryCode,
                                            @Param("size") int size,
                                            @Param("offset") int offset);

    // 무한스크롤 방식: id < cursor 기준 keyset. limit은 service가 size+1로 넘겨 hasNext를 판별한다.
    List<PostSummaryRow> findActivePostSlice(@Param("categoryCode") String categoryCode,
                                             @Param("cursor") Long cursor,
                                             @Param("limit") int limit);

    // 홈 인기 글: 좋아요 순 상위 N건 (카테고리 무관)
    List<PostSummaryRow> findPopularPosts(@Param("limit") int limit);

    // 상세는 상태와 무관하게 조회하고, BLOCKED/DELETED 노출 판단은 service가 한다.
    Optional<PostDetailRow> findPostDetail(@Param("postId") long postId);

    int increaseViewCount(@Param("postId") long postId);

    // 전 상태 조회 — DELETED 마스킹·관리자 표시 등 노출 규칙은 service가 정한다.
    List<CommentRow> findCommentsByPost(@Param("postId") long postId);

    int updatePost(@Param("postId") long postId,
                   @Param("categoryId") long categoryId,
                   @Param("title") String title,
                   @Param("content") String content);

    int softDeletePost(@Param("postId") long postId);

    int softDeleteComment(@Param("commentId") long commentId);

    // 글 작성·수정 저장용 — posts.category_id에 넣을 id만 필요하다. code는 UNIQUE라 최대 1건이다.
    Optional<Long> findActiveCategoryIdByCode(@Param("code") String code);

    int insertPost(Post post);

    Optional<Comment> findCommentById(@Param("commentId") long commentId);

    int insertComment(Comment comment);

    boolean existsPostLike(@Param("postId") long postId, @Param("memberId") long memberId);

    int insertPostLike(@Param("postId") long postId, @Param("memberId") long memberId);

    int deletePostLike(@Param("postId") long postId, @Param("memberId") long memberId);

    // 좋아요 토글과 같은 트랜잭션에서 like_count를 함께 증감해 파생값을 동기화한다.
    int addLikeCount(@Param("postId") long postId, @Param("delta") int delta);

    long findLikeCount(@Param("postId") long postId);

    // ---- 신고 ----

    boolean existsReport(@Param("postId") long postId, @Param("reporterId") long reporterId);

    int insertReport(@Param("postId") long postId,
                     @Param("reporterId") long reporterId,
                     @Param("reason") String reason);

    // ---- 관리자: 상태 무관 전체 글 대상 ----

    long countPostsForAdmin(@Param("status") String status,
                            @Param("categoryCode") String categoryCode,
                            @Param("title") String title,
                            @Param("memberIds") List<Long> memberIds);

    List<AdminPostSummaryRow> findPostPageForAdmin(@Param("status") String status,
                                                   @Param("categoryCode") String categoryCode,
                                                   @Param("title") String title,
                                                   @Param("memberIds") List<Long> memberIds,
                                                   @Param("size") int size,
                                                   @Param("offset") int offset);

    Optional<AdminPostDetailRow> findPostDetailForAdmin(@Param("postId") long postId);

    List<ReportRow> findReports(@Param("postId") long postId);

    // 상태 전이 검증은 service가 끝낸 뒤 호출한다. 시간은 규약대로 DB가 채운다.
    int blockPost(@Param("postId") long postId,
                  @Param("reason") String reason,
                  @Param("adminId") long adminId);

    int unblockPost(@Param("postId") long postId);

    // 회원별 작성 글 수 배치 집계 (member 관리자 화면이 공개 계약으로 사용한다). 삭제·차단 글은 제외한다.
    List<MemberCountRow> countActivePostsByMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
