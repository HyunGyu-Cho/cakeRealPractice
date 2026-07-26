package com.cakeshop.domain.member.service;

import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.dto.view.AdminMemberDetailView;
import com.cakeshop.domain.member.dto.view.AdminMemberListView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 관리 — 목록·검색, 상세, 이용 제한·해제.
 *
 * <p>활동 지표는 남의 테이블을 JOIN하지 않고 order·review·community의 공개 계약으로 받는다.
 * 상태 전이 규칙(관리자 계정 제재 금지·탈퇴 회원 변경 금지)은 전부 이 서비스가 소유한다.
 */
@Service
public class MemberAdminService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final MemberMapper memberMapper;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final CommunityService communityService;

    public MemberAdminService(MemberMapper memberMapper, OrderService orderService,
                              ReviewService reviewService, CommunityService communityService) {
        this.memberMapper = memberMapper;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.communityService = communityService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminMemberListView> getMemberPage(AdminMemberSearchForm cond,
                                                         PageRequest pageRequest) {
        long total = memberMapper.countAdminMembers(cond);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<Member> members = memberMapper.findAdminMemberPage(
            cond, pageRequest.getSize(), pageRequest.getOffset());

        // 한 페이지의 id를 모아 배치 1회로 받는다(회원마다 조회하면 N+1).
        Set<Long> memberIds = members.stream().map(Member::getId).collect(Collectors.toSet());
        Map<Long, Long> orderCounts = orderService.getOrderCountMap(memberIds);

        List<AdminMemberListView> content = members.stream()
            .map(member -> {
                MemberStatus status = MemberStatus.valueOf(member.getStatus());
                return new AdminMemberListView(
                    member.getId(), member.getNickname(), member.getEmail(), member.getPhone(),
                    member.getCreatedAt(), orderCounts.getOrDefault(member.getId(), 0L),
                    status.name(), status.label(), ROLE_ADMIN.equals(member.getRole()));
            })
            .toList();
        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailView getMemberDetail(Long memberId) {
        Member member = findMember(memberId);
        MemberStatus status = MemberStatus.valueOf(member.getStatus());
        Set<Long> ids = Set.of(member.getId());

        return new AdminMemberDetailView(
            member.getId(), member.getEmail(), member.getNickname(), member.getPhone(),
            member.getRole(), status.name(), status.label(),
            member.getCreatedAt(), member.getSuspendedAt(), member.getSuspendedReason(),
            member.getWithdrawnAt(),
            orderService.getOrderCountMap(ids).getOrDefault(member.getId(), 0L),
            reviewService.getReviewCountMap(ids).getOrDefault(member.getId(), 0L),
            communityService.getPostCountMap(ids).getOrDefault(member.getId(), 0L),
            canTransition(member, MemberStatus.SUSPENDED),
            canTransition(member, MemberStatus.ACTIVE));
    }

    /** 이용 제한. 사유는 폼 검증이 끝난 값이며 제재 시각은 DDL이 아니라 UPDATE 문이 세팅한다. */
    @Transactional
    public void suspend(Long memberId, String reason) {
        Member member = findMember(memberId);
        verifySuspendable(member);
        if (memberMapper.suspend(memberId, reason) != 1) {
            // 조회 시점 이후 다른 관리자가 먼저 바꾼 경우.
            throw new BusinessException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /** 제한 해제. 사유와 제재 시각을 함께 지운다 — 이력 테이블은 이번 범위 밖이다. */
    @Transactional
    public void unsuspend(Long memberId) {
        Member member = findMember(memberId);
        verifyWithdrawn(member);
        if (!MemberStatus.SUSPENDED.matches(member.getStatus())
            || memberMapper.unsuspend(memberId) != 1) {
            throw new BusinessException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private void verifySuspendable(Member member) {
        verifyWithdrawn(member);
        // 관리자끼리 잠그거나 자기 계정을 잠그는 사고를 막는다.
        if (ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(MemberErrorCode.CANNOT_SUSPEND_ADMIN);
        }
        if (!MemberStatus.ACTIVE.matches(member.getStatus())) {
            throw new BusinessException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private void verifyWithdrawn(Member member) {
        if (MemberStatus.WITHDRAWN.matches(member.getStatus())) {
            throw new BusinessException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
    }

    /** 화면이 조건을 다시 판단하지 않도록 버튼 노출 여부를 여기서 계산한다. */
    private boolean canTransition(Member member, MemberStatus next) {
        if (next == MemberStatus.SUSPENDED && ROLE_ADMIN.equals(member.getRole())) {
            return false;
        }
        return MemberStatus.valueOf(member.getStatus()).canAdminTransitionTo(next);
    }

    private Member findMember(Long memberId) {
        return memberMapper.findById(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
    }
}
