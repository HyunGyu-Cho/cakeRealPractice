package com.cakeshop.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.dto.view.AdminMemberDetailView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MemberAdminControllerTests {

    private MockMvc mockMvc;
    private MemberAdminService memberAdminService;

    @BeforeEach
    void setUp() {
        memberAdminService = mock(MemberAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberAdminController(memberAdminService))
            .build();
    }

    private AdminMemberDetailView detail() {
        return new AdminMemberDetailView(1L, "user@cakeshop.local", "회원", "010-0000-0000",
            "USER", "ACTIVE", "정상", LocalDateTime.now(), null, null, null,
            0L, 0L, 0L, true, false);
    }

    @Test
    void 목록은_검색_조건을_페이지_링크에_유지한다() throws Exception {
        when(memberAdminService.getMemberPage(any(AdminMemberSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 10), 0));

        mockMvc.perform(get("/admin/members").param("name", "홍").param("status", "SUSPENDED"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/member/list"))
            .andExpect(model().attribute("extraQuery",
                org.hamcrest.Matchers.containsString("status=SUSPENDED")));
    }

    @Test
    void 알_수_없는_상태값은_조건에서_떨군다() throws Exception {
        when(memberAdminService.getMemberPage(any(AdminMemberSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 10), 0));

        mockMvc.perform(get("/admin/members").param("status", "DROP_TABLE"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("extraQuery", ""));
    }

    @Test
    void 상세는_회원과_제재_폼을_모델에_담는다() throws Exception {
        when(memberAdminService.getMemberDetail(1L)).thenReturn(detail());

        mockMvc.perform(get("/admin/members/1"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/member/detail"))
            .andExpect(model().attributeExists("member", "suspendForm"));
    }

    @Test
    void 제재_성공은_상세로_리다이렉트하고_성공_메시지를_남긴다() throws Exception {
        mockMvc.perform(post("/admin/members/1/suspend").param("reason", "약관 위반"))
            .andExpect(redirectedUrl("/admin/members/1"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(memberAdminService).suspend(1L, "약관 위반");
    }

    @Test
    void 사유가_비면_리다이렉트하지_않고_상세를_재렌더한다() throws Exception {
        when(memberAdminService.getMemberDetail(1L)).thenReturn(detail());

        mockMvc.perform(post("/admin/members/1/suspend").param("reason", " "))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/member/detail"))
            .andExpect(model().attributeHasFieldErrors("suspendForm", "reason"));

        verify(memberAdminService, never()).suspend(anyLong(), anyString());
    }

    @Test
    void 업무_규칙_위반은_에러_메시지로_돌려준다() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.CANNOT_SUSPEND_ADMIN))
            .when(memberAdminService).suspend(anyLong(), anyString());

        mockMvc.perform(post("/admin/members/9/suspend").param("reason", "사유"))
            .andExpect(redirectedUrl("/admin/members/9"))
            .andExpect(flash().attribute("errorMessage",
                MemberErrorCode.CANNOT_SUSPEND_ADMIN.message()));
    }

    @Test
    void 제한_해제는_상세로_리다이렉트한다() throws Exception {
        mockMvc.perform(post("/admin/members/2/unsuspend"))
            .andExpect(redirectedUrl("/admin/members/2"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(memberAdminService).unsuspend(2L);
    }
}
