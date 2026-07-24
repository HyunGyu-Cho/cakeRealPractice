package com.cakeshop.domain.community.service;

import com.cakeshop.domain.community.dto.form.PostBlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailRow;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostSummaryRow;
import com.cakeshop.domain.community.dto.view.AdminPostSummaryView;
import com.cakeshop.domain.community.dto.view.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.ReportRow;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 커뮤니티 관리 — 상태 무관 조회, 제재(BLOCK)·해제 전이, 댓글 삭제를 소유한다. */
@Service
public class CommunityAdminService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final CommunityMapper communityMapper;
    private final MemberService memberService;

    public CommunityAdminService(CommunityMapper communityMapper, MemberService memberService) {
        this.communityMapper = communityMapper;
        this.memberService = memberService;
    }

    /** 상태 필터 파라미터는 사용자 입력이므로 enum 이름이 아니면 전체(null)로 취급한다. */
    public String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (PostStatus candidate : PostStatus.values()) {
            if (candidate.name().equals(status)) {
                return status;
            }
        }
        return null;
    }

    /** 작성자(닉네임)·제목 검색 포함 목록. 작성자 해석은 member 공개 계약을 통한다. */
    @Transactional(readOnly = true)
    public PageResult<AdminPostSummaryView> getPostPage(String status, String categoryCode,
                                                        String title, String writer, PageRequest pageRequest) {
        String titleKeyword = (title == null || title.isBlank()) ? null : title.trim();

        List<Long> writerIds = null;
        if (writer != null && !writer.isBlank()) {
            writerIds = memberService.searchMemberIdsByNickname(writer.trim());
            if (writerIds.isEmpty()) {
                // 일치하는 회원이 없으면 조회 없이 빈 결과 (IN () 는 유효한 SQL이 아니다)
                return new PageResult<>(List.of(), pageRequest, 0);
            }
        }

        long total = communityMapper.countPostsForAdmin(status, categoryCode, titleKeyword, writerIds);
        List<AdminPostSummaryRow> rows = communityMapper.findPostPageForAdmin(
            status, categoryCode, titleKeyword, writerIds, pageRequest.getSize(), pageRequest.getOffset());

        Set<Long> memberIds = rows.stream()
            .map(AdminPostSummaryRow::getMemberId)
            .collect(Collectors.toSet());
        Map<Long, String> nicknames = memberService.getNicknameMap(memberIds);

        List<AdminPostSummaryView> views = rows.stream()
            .map(row -> new AdminPostSummaryView(
                row.getId(),
                row.getCategoryName(),
                row.getTitle(),
                nicknames.getOrDefault(row.getMemberId(), "알 수 없음"),
                row.getCreatedAt().format(DATE_FORMATTER),
                row.getStatus().name(),
                row.getStatus().label()))
            .toList();
        return new PageResult<>(views, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public AdminPostDetailView getPostDetail(long postId) {
        AdminPostDetailRow post = findPost(postId);
        List<CommentRow> comments = communityMapper.findCommentsByPost(postId);
        List<ReportRow> reports = communityMapper.findReports(postId);

        Set<Long> memberIds = new HashSet<>();
        memberIds.add(post.getMemberId());
        if (post.getBlockedBy() != null) {
            memberIds.add(post.getBlockedBy());
        }
        comments.forEach(comment -> memberIds.add(comment.getMemberId()));
        reports.forEach(report -> memberIds.add(report.getReporterId()));
        Map<Long, String> nicknames = memberService.getNicknameMap(memberIds);

        // 관리자는 삭제 댓글도 원문 그대로 보되 deleted 표식으로 구분한다.
        List<CommentView> commentViews = comments.stream()
            .map(comment -> new CommentView(
                comment.getId(),
                nicknames.getOrDefault(comment.getMemberId(), "알 수 없음"),
                comment.getContent(),
                comment.getCreatedAt().format(DATE_FORMATTER),
                comment.getParentCommentId() != null,
                false,
                comment.getStatus() == CommentStatus.DELETED))
            .toList();

        List<ReportView> reportViews = reports.stream()
            .map(report -> new ReportView(
                report.getId(),
                nicknames.getOrDefault(report.getReporterId(), "알 수 없음"),
                report.getReason(),
                reportStatusLabel(report.getStatus()),
                report.getCreatedAt().format(DATE_FORMATTER)))
            .toList();

        return new AdminPostDetailView(
            post.getId(),
            post.getCategoryName(),
            post.getTitle(),
            post.getContent(),
            nicknames.getOrDefault(post.getMemberId(), "알 수 없음"),
            post.getViewCount(),
            post.getLikeCount(),
            post.getStatus().name(),
            post.getStatus().label(),
            post.getBlockedAt() == null ? null : post.getBlockedAt().format(DATE_TIME_FORMATTER),
            post.getBlockedReason(),
            post.getBlockedBy() == null ? null : nicknames.getOrDefault(post.getBlockedBy(), "알 수 없음"),
            post.getCreatedAt().format(DATE_TIME_FORMATTER),
            reportViews,
            commentViews);
    }

    /** 제재: ACTIVE → BLOCKED 전이만 허용한다. */
    @Transactional
    public void blockPost(long adminId, long postId, PostBlockForm form) {
        AdminPostDetailRow post = findPost(postId);
        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new BusinessException(CommunityErrorCode.INVALID_STATUS_CHANGE);
        }
        communityMapper.blockPost(postId, form.getReason().trim(), adminId);
    }

    /** 제재 해제: BLOCKED → ACTIVE 전이만 허용한다. */
    @Transactional
    public void unblockPost(long postId) {
        AdminPostDetailRow post = findPost(postId);
        if (post.getStatus() != PostStatus.BLOCKED) {
            throw new BusinessException(CommunityErrorCode.INVALID_STATUS_CHANGE);
        }
        communityMapper.unblockPost(postId);
    }

    /** 관리자 댓글 삭제 — 작성자 검증 없이 소프트 삭제한다. */
    @Transactional
    public void deleteComment(long postId, long commentId) {
        findPost(postId);
        Comment comment = communityMapper.findCommentById(commentId)
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPostId().equals(postId) || comment.getStatus() != CommentStatus.ACTIVE) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
        communityMapper.softDeleteComment(commentId);
    }

    private String reportStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "접수";
            case "ACCEPTED" -> "처리 완료";
            case "REJECTED" -> "반려";
            default -> status;
        };
    }

    private AdminPostDetailRow findPost(long postId) {
        return communityMapper.findPostDetailForAdmin(postId)
            .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }
}
