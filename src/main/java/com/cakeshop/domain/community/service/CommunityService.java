package com.cakeshop.domain.community.service;

import com.cakeshop.domain.community.dto.form.CommentCreateForm;
import com.cakeshop.domain.community.dto.form.PostCreateForm;
import com.cakeshop.domain.community.dto.form.PostReportForm;
import com.cakeshop.domain.community.dto.view.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.LikeResultView;
import com.cakeshop.domain.community.dto.view.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostSliceView;
import com.cakeshop.domain.community.dto.view.PostSummaryRow;
import com.cakeshop.domain.community.dto.view.PostSummaryView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostCategory;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.common.stats.MemberCountRow;
import com.cakeshop.global.error.BusinessException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // 무한스크롤 한 번에 내려주는 기본·최대 건수
    public static final int SLICE_DEFAULT_SIZE = 10;
    private static final int SLICE_MAX_SIZE = 30;

    private final CommunityMapper communityMapper;
    private final MemberService memberService;

    public CommunityService(CommunityMapper communityMapper, MemberService memberService) {
        this.communityMapper = communityMapper;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public List<PostCategory> getActiveCategories() {
        return communityMapper.findActiveCategories();
    }

    /** 필터 파라미터는 사용자 입력이므로 실제 카테고리 code가 아니면 전체(null)로 취급한다. */
    @Transactional(readOnly = true)
    public String normalizeCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        return getActiveCategories().stream()
            .map(PostCategory::getCode)
            .filter(code -> code.equals(categoryCode))
            .findFirst()
            .orElse(null);
    }

    /** 페이지 번호 방식: 전체 건수 + LIMIT/OFFSET. 페이지 이동 UI에 totalPages가 필요해 카운트 쿼리를 함께 낸다. */
    @Transactional(readOnly = true)
    public PageResult<PostSummaryView> getPostPage(String categoryCode, PageRequest pageRequest) {
        long total = communityMapper.countActivePosts(categoryCode);
        List<PostSummaryRow> rows =
            communityMapper.findActivePostPage(categoryCode, pageRequest.getSize(), pageRequest.getOffset());
        return new PageResult<>(toViews(rows), pageRequest, total);
    }

    /**
     * 무한스크롤 방식: 마지막 글 id(cursor) 이후를 keyset으로 조회한다.
     * OFFSET과 달리 스크롤 중 새 글이 끼어들어도 같은 글이 중복 표시되지 않고, 카운트 쿼리도 없다.
     * size+1건을 조회해 다음 페이지 존재 여부(hasNext)를 추가 쿼리 없이 판별한다.
     */
    @Transactional(readOnly = true)
    public PostSliceView getPostSlice(String categoryCode, Long cursor, Integer size) {
        String category = normalizeCategory(categoryCode);
        int sliceSize = (size == null || size < 1) ? SLICE_DEFAULT_SIZE : Math.min(size, SLICE_MAX_SIZE);

        List<PostSummaryRow> rows = communityMapper.findActivePostSlice(category, cursor, sliceSize + 1);
        boolean hasNext = rows.size() > sliceSize;
        if (hasNext) {
            rows = rows.subList(0, sliceSize);
        }
        Long nextCursor = (hasNext && !rows.isEmpty()) ? rows.get(rows.size() - 1).getId() : null;
        return new PostSliceView(toViews(rows), hasNext, nextCursor);
    }

    /**
     * [공개 계약] 회원별 작성 글 수 배치 조회. member 관리자 상세가 첫 사용처다.
     * 상대 도메인이 posts를 JOIN하지 않도록 집계는 여기서 끝낸다(삭제·차단 글 제외).
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getPostCountMap(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return communityMapper.countActivePostsByMemberIds(memberIds).stream()
            .collect(Collectors.toMap(MemberCountRow::memberId, MemberCountRow::count));
    }

    /** [공개 계약] 홈 메인 노출용 — 공개 글 중 좋아요가 많은 순 상위 N건. */
    @Transactional(readOnly = true)
    public List<PostSummaryView> getPopularPosts(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return toViews(communityMapper.findPopularPosts(Math.min(limit, SLICE_MAX_SIZE)));
    }

    /**
     * 상세 조회. BLOCKED는 사유가 있는 차단이므로 403, DELETED·미존재는 존재를 숨기는 404로 응답한다.
     * 조회수 증가가 있어 readOnly가 아니다.
     *
     * @param memberId      로그인 회원 id (비로그인 null) — 좋아요 여부 표시용
     * @param increaseView  최초 진입만 true. 댓글 검증 실패 재렌더에서 조회수가 또 오르지 않게 한다.
     */
    @Transactional
    public PostDetailView getPostDetail(long postId, Long memberId, boolean increaseView) {
        PostDetailRow post = findActivePost(postId);
        if (increaseView) {
            communityMapper.increaseViewCount(postId);
        }

        List<CommentRow> comments = communityMapper.findCommentsByPost(postId);

        // 글 작성자 + 댓글 작성자 닉네임을 member 공개 계약으로 한 번에 조회한다.
        Set<Long> memberIds = new HashSet<>();
        memberIds.add(post.getMemberId());
        comments.forEach(comment -> memberIds.add(comment.getMemberId()));
        Map<Long, String> nicknames = memberService.getNicknameMap(memberIds);

        // 삭제 댓글 노출 규칙: 살아있는 답글이 달린 최상위 댓글만 마스킹된 자리로 남기고, 나머지는 숨긴다.
        Set<Long> parentIdsWithActiveReply = comments.stream()
            .filter(c -> c.getParentCommentId() != null && c.getStatus() == CommentStatus.ACTIVE)
            .map(CommentRow::getParentCommentId)
            .collect(Collectors.toSet());

        List<CommentView> commentViews = new ArrayList<>();
        for (CommentRow comment : comments) {
            boolean deleted = comment.getStatus() == CommentStatus.DELETED;
            boolean reply = comment.getParentCommentId() != null;
            if (deleted && (reply || !parentIdsWithActiveReply.contains(comment.getId()))) {
                continue;
            }
            commentViews.add(new CommentView(
                comment.getId(),
                deleted ? "삭제된 댓글" : nicknames.getOrDefault(comment.getMemberId(), "알 수 없음"),
                deleted ? "작성자가 삭제한 댓글입니다." : comment.getContent(),
                comment.getCreatedAt().format(DATE_FORMATTER),
                reply,
                !deleted && memberId != null && memberId.equals(comment.getMemberId()),
                deleted));
        }

        boolean likedByMe = memberId != null && communityMapper.existsPostLike(postId, memberId);

        return new PostDetailView(
            post.getId(),
            post.getCategoryCode(),
            post.getCategoryName(),
            post.getTitle(),
            post.getContent(),
            nicknames.getOrDefault(post.getMemberId(), "알 수 없음"),
            post.getViewCount() + (increaseView ? 1 : 0),  // 방금 증가분을 화면에 반영 (재조회 대신 계산)
            post.getLikeCount(),
            likedByMe,
            memberId != null && memberId.equals(post.getMemberId()),
            post.getCreatedAt().format(DATE_FORMATTER),
            commentViews);
    }

    /** 수정 화면 진입용 — 작성자 본인 + ACTIVE 글만 허용하고 기존 값으로 폼을 채운다. */
    @Transactional(readOnly = true)
    public PostCreateForm getPostForEdit(long memberId, long postId) {
        PostDetailRow post = findActivePost(postId);
        validateAuthor(post.getMemberId(), memberId);
        PostCreateForm form = new PostCreateForm();
        form.setCategoryCode(post.getCategoryCode());
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        return form;
    }

    @Transactional
    public void updatePost(long memberId, long postId, PostCreateForm form) {
        PostDetailRow post = findActivePost(postId);
        validateAuthor(post.getMemberId(), memberId);
        PostCategory category = communityMapper.findCategoryByCode(form.getCategoryCode())
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND));
        communityMapper.updatePost(postId, category.getId(), form.getTitle().trim(), form.getContent().trim());
    }

    /** 작성자 삭제는 소프트 삭제(DELETED)다. 목록·상세 노출만 끊고 행은 남긴다. */
    @Transactional
    public void deletePost(long memberId, long postId) {
        PostDetailRow post = findActivePost(postId);
        validateAuthor(post.getMemberId(), memberId);
        communityMapper.softDeletePost(postId);
    }

    @Transactional
    public void deleteComment(long memberId, long postId, long commentId) {
        findActivePost(postId);
        Comment comment = communityMapper.findCommentById(commentId)
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPostId().equals(postId) || comment.getStatus() != CommentStatus.ACTIVE) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
        validateAuthor(comment.getMemberId(), memberId);
        communityMapper.softDeleteComment(commentId);
    }

    /** 신고 접수 — 본인 글·중복 신고는 거부한다. 동시 중복은 uk_post_reports_post_reporter가 최종 방어. */
    @Transactional
    public void reportPost(long memberId, long postId, PostReportForm form) {
        PostDetailRow post = findActivePost(postId);
        if (post.getMemberId().equals(memberId)) {
            throw new BusinessException(CommunityErrorCode.CANNOT_REPORT_OWN_POST);
        }
        if (communityMapper.existsReport(postId, memberId)) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }
        communityMapper.insertReport(postId, memberId, form.getReason().trim());
    }

    private void validateAuthor(Long authorId, long memberId) {
        if (!authorId.equals(memberId)) {
            throw new BusinessException(CommunityErrorCode.NOT_AUTHOR);
        }
    }

    /** 새 글 등록. 저장값은 code로 받아 category id로 변환하고, 시작 상태는 ACTIVE다. */
    @Transactional
    public long createPost(long memberId, PostCreateForm form) {
        PostCategory category = communityMapper.findCategoryByCode(form.getCategoryCode())
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND));

        Post post = new Post();
        post.setMemberId(memberId);
        post.setCategoryId(category.getId());
        post.setTitle(form.getTitle().trim());
        post.setContent(form.getContent().trim());
        post.setStatus(PostStatus.ACTIVE);
        communityMapper.insertPost(post);
        return post.getId();
    }

    /** 댓글 등록. 답글은 같은 글의 정상 최상위 댓글에만 허용한다(1단계 제한). */
    @Transactional
    public void addComment(long memberId, long postId, CommentCreateForm form) {
        findActivePost(postId);

        if (form.getParentCommentId() != null) {
            Comment parent = communityMapper.findCommentById(form.getParentCommentId())
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.PARENT_COMMENT_NOT_FOUND));
            boolean invalidParent = !parent.getPostId().equals(postId)
                || parent.getStatus() != CommentStatus.ACTIVE
                || parent.getParentCommentId() != null;
            if (invalidParent) {
                throw new BusinessException(CommunityErrorCode.PARENT_COMMENT_NOT_FOUND);
            }
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setMemberId(memberId);
        comment.setParentCommentId(form.getParentCommentId());
        comment.setContent(form.getContent().trim());
        comment.setStatus(CommentStatus.ACTIVE);
        communityMapper.insertComment(comment);
    }

    /** 좋아요 토글. post_likes가 정본이고 posts.like_count는 같은 트랜잭션에서 증감하는 파생값이다. */
    @Transactional
    public LikeResultView toggleLike(long memberId, long postId) {
        findActivePost(postId);

        boolean liked;
        if (communityMapper.existsPostLike(postId, memberId)) {
            communityMapper.deletePostLike(postId, memberId);
            communityMapper.addLikeCount(postId, -1);
            liked = false;
        } else {
            // 동시 중복 클릭은 uk_post_likes_post_member UNIQUE 제약이 최종 방어한다.
            communityMapper.insertPostLike(postId, memberId);
            communityMapper.addLikeCount(postId, 1);
            liked = true;
        }
        return new LikeResultView(liked, communityMapper.findLikeCount(postId));
    }

    // 노출 가능한(ACTIVE) 글만 통과시키는 공통 관문
    private PostDetailRow findActivePost(long postId) {
        PostDetailRow post = communityMapper.findPostDetail(postId)
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
        if (post.getStatus() == PostStatus.BLOCKED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }
        if (post.getStatus() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    // 닉네임은 member 도메인 공개 계약(getNicknameMap)으로 한 번에 받아 행별 조회(N+1)를 피한다.
    private List<PostSummaryView> toViews(List<PostSummaryRow> rows) {
        Set<Long> memberIds = rows.stream()
            .map(PostSummaryRow::getMemberId)
            .collect(Collectors.toSet());
        Map<Long, String> nicknames = memberService.getNicknameMap(memberIds);

        return rows.stream()
            .map(row -> new PostSummaryView(
                row.getId(),
                row.getCategoryCode(),
                row.getCategoryName(),
                row.getTitle(),
                nicknames.getOrDefault(row.getMemberId(), "알 수 없음"),
                row.getLikeCount(),
                row.getCommentCount(),
                row.getCreatedAt().format(DATE_FORMATTER)))
            .toList();
    }
}
