package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberAdminServiceTests {

    private MemberMapper memberMapper;
    private OrderService orderService;
    private ReviewService reviewService;
    private CommunityService communityService;
    private MemberAdminService memberAdminService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        orderService = mock(OrderService.class);
        reviewService = mock(ReviewService.class);
        communityService = mock(CommunityService.class);
        memberAdminService = new MemberAdminService(
            memberMapper, orderService, reviewService, communityService);
    }

    private Member member(Long id, String role, MemberStatus status) {
        Member member = new Member();
        member.setId(id);
        member.setEmail("user" + id + "@cakeshop.local");
        member.setNickname("회원" + id);
        member.setPhone("010-0000-0000");
        member.setRole(role);
        member.setStatus(status.name());
        member.setCreatedAt(LocalDateTime.now());
        return member;
    }

    @Test
    void 목록은_주문_건수를_회원마다가_아니라_배치_한_번으로_받는다() {
        AdminMemberSearchForm cond = new AdminMemberSearchForm();
        when(memberMapper.countAdminMembers(cond)).thenReturn(2L);
        when(memberMapper.findAdminMemberPage(any(), anyInt(), anyInt()))
            .thenReturn(List.of(member(1L, "USER", MemberStatus.ACTIVE),
                                member(2L, "USER", MemberStatus.SUSPENDED)));
        when(orderService.getOrderCountMap(any())).thenReturn(Map.of(1L, 3L));

        PageResult<AdminMemberListView> page =
            memberAdminService.getMemberPage(cond, new PageRequest(1, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).orderCount()).isEqualTo(3L);
        // 주문이 없는 회원은 0으로 채운다(누락된 키).
        assertThat(page.getContent().get(1).orderCount()).isZero();
        assertThat(page.getContent().get(1).statusLabel()).isEqualTo("이용 제한");
        verify(orderService).getOrderCountMap(any());
    }

    @Test
    void 검색_결과가_없으면_타_도메인_집계를_호출하지_않는다() {
        AdminMemberSearchForm cond = new AdminMemberSearchForm();
        when(memberMapper.countAdminMembers(cond)).thenReturn(0L);

        assertThat(memberAdminService.getMemberPage(cond, new PageRequest(1, 10)).getContent())
            .isEmpty();
        verify(memberMapper, never()).findAdminMemberPage(any(), anyInt(), anyInt());
        verify(orderService, never()).getOrderCountMap(any());
    }

    @Test
    void 상세는_활동_지표를_각_도메인_공개_계약으로_받는다() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member(1L, "USER", MemberStatus.ACTIVE)));
        when(orderService.getOrderCountMap(any())).thenReturn(Map.of(1L, 4L));
        when(reviewService.getReviewCountMap(any())).thenReturn(Map.of(1L, 2L));
        when(communityService.getPostCountMap(any())).thenReturn(Map.of(1L, 7L));

        AdminMemberDetailView view = memberAdminService.getMemberDetail(1L);

        assertThat(view.orderCount()).isEqualTo(4L);
        assertThat(view.reviewCount()).isEqualTo(2L);
        assertThat(view.postCount()).isEqualTo(7L);
        // ACTIVE 회원은 제재만 가능하고 해제 버튼은 뜨지 않는다.
        assertThat(view.suspendable()).isTrue();
        assertThat(view.releasable()).isFalse();
    }

    @Test
    void 관리자_계정은_제재할_수_없다() {
        when(memberMapper.findById(9L)).thenReturn(Optional.of(member(9L, "ADMIN", MemberStatus.ACTIVE)));

        assertThatThrownBy(() -> memberAdminService.suspend(9L, "사유"))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.CANNOT_SUSPEND_ADMIN.message());
        verify(memberMapper, never()).suspend(anyLong(), anyString());
    }

    @Test
    void 관리자_계정_상세에는_제재_버튼이_뜨지_않는다() {
        when(memberMapper.findById(9L)).thenReturn(Optional.of(member(9L, "ADMIN", MemberStatus.ACTIVE)));
        when(orderService.getOrderCountMap(any())).thenReturn(Map.of());
        when(reviewService.getReviewCountMap(any())).thenReturn(Map.of());
        when(communityService.getPostCountMap(any())).thenReturn(Map.of());

        assertThat(memberAdminService.getMemberDetail(9L).suspendable()).isFalse();
    }

    @Test
    void 탈퇴_회원은_제재도_해제도_할_수_없다() {
        when(memberMapper.findById(3L))
            .thenReturn(Optional.of(member(3L, "USER", MemberStatus.WITHDRAWN)));

        assertThatThrownBy(() -> memberAdminService.suspend(3L, "사유"))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.WITHDRAWN_MEMBER.message());
        assertThatThrownBy(() -> memberAdminService.unsuspend(3L))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.WITHDRAWN_MEMBER.message());
        verify(memberMapper, never()).suspend(anyLong(), anyString());
        verify(memberMapper, never()).unsuspend(anyLong());
    }

    @Test
    void 이미_제재된_회원을_다시_제재하지_않는다() {
        when(memberMapper.findById(2L))
            .thenReturn(Optional.of(member(2L, "USER", MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> memberAdminService.suspend(2L, "사유"))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.INVALID_STATUS_TRANSITION.message());
    }

    @Test
    void 제재는_사유와_함께_조건부_UPDATE로_반영된다() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member(1L, "USER", MemberStatus.ACTIVE)));
        when(memberMapper.suspend(1L, "약관 위반")).thenReturn(1);

        memberAdminService.suspend(1L, "약관 위반");

        verify(memberMapper).suspend(1L, "약관 위반");
    }

    @Test
    void 다른_관리자가_먼저_상태를_바꿨으면_실패로_처리한다() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member(1L, "USER", MemberStatus.ACTIVE)));
        when(memberMapper.suspend(anyLong(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> memberAdminService.suspend(1L, "사유"))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.INVALID_STATUS_TRANSITION.message());
    }

    @Test
    void 제한_해제는_제재된_회원만_대상으로_한다() {
        when(memberMapper.findById(2L))
            .thenReturn(Optional.of(member(2L, "USER", MemberStatus.SUSPENDED)));
        when(memberMapper.unsuspend(2L)).thenReturn(1);

        memberAdminService.unsuspend(2L);
        verify(memberMapper).unsuspend(2L);

        when(memberMapper.findById(1L)).thenReturn(Optional.of(member(1L, "USER", MemberStatus.ACTIVE)));
        assertThatThrownBy(() -> memberAdminService.unsuspend(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.INVALID_STATUS_TRANSITION.message());
    }

    @Test
    void 없는_회원은_찾을_수_없다() {
        when(memberMapper.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAdminService.getMemberDetail(404L))
            .isInstanceOf(BusinessException.class)
            .hasMessage(MemberErrorCode.NOT_FOUND.message());
    }
}
