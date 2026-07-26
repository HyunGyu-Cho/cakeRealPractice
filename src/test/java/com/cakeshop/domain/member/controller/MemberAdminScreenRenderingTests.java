package com.cakeshop.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.dto.view.AdminMemberDetailView;
import com.cakeshop.domain.member.dto.view.AdminMemberListView;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 관리자 회원 화면이 실제 Thymeleaf 엔진을 통과하는지 확인한다.
 * 컨트롤러 단위 테스트는 뷰 이름만 검증해 템플릿 문법 오류를 놓친다.
 */
@SpringBootTest
@ActiveProfiles("local")
class MemberAdminScreenRenderingTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 10, 0);

    @Autowired private WebApplicationContext context;
    @MockitoBean private MemberAdminService memberAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void memberListRenders() throws Exception {
        when(memberAdminService.getMemberPage(any(AdminMemberSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(
                List.of(listView(1L, "ACTIVE", "정상", false),
                        listView(2L, "SUSPENDED", "이용 제한", false),
                        listView(3L, "WITHDRAWN", "탈퇴", false),
                        listView(4L, "ACTIVE", "정상", true)),
                new PageRequest(1, 10), 4));

        mockMvc.perform(get("/admin/members").with(user(admin())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    /** 상태별로 노출 블록이 갈리므로 세 상태를 모두 렌더해 본다. */
    @Test
    void memberDetailRendersForEveryStatus() throws Exception {
        when(memberAdminService.getMemberDetail(1L))
            .thenReturn(detailView("ACTIVE", "정상", true, false, null));
        when(memberAdminService.getMemberDetail(2L))
            .thenReturn(detailView("SUSPENDED", "이용 제한", false, true, "약관 위반"));
        when(memberAdminService.getMemberDetail(3L))
            .thenReturn(detailView("WITHDRAWN", "탈퇴", false, false, null));

        for (long id : new long[] {1L, 2L, 3L}) {
            mockMvc.perform(get("/admin/members/" + id).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    private AdminMemberListView listView(Long id, String status, String label, boolean admin) {
        return new AdminMemberListView(id, "회원" + id, "user" + id + "@cakeshop.local",
            "010-0000-000" + id, NOW, 3L, status, label, admin);
    }

    private AdminMemberDetailView detailView(String status, String label,
                                             boolean suspendable, boolean releasable, String reason) {
        return new AdminMemberDetailView(1L, "user@cakeshop.local", "회원", "010-0000-0000",
            "USER", status, label, NOW,
            releasable ? NOW : null, reason,
            "WITHDRAWN".equals(status) ? NOW : null,
            4L, 2L, 7L, suspendable, releasable);
    }

    private MemberDetails admin() {
        return new MemberDetails(1L, "admin@cakeshop.local", "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
