package com.cakeshop.global.error;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * JSON 컨트롤러와 화면 컨트롤러에 서로 다른 advice 가 걸리는지 확인한다.
 *
 * <p>여기서 고정하는 두 가지는 selector 실수로 조용히 깨지는 종류다 —
 * JSON 요청에 HTML 오류 페이지가 나가는 것, 그리고 헤더용 {@code @ModelAttribute} 가
 * JSON 요청에서도 돌아 쓰이지 않는 COUNT 쿼리를 내는 것.
 */
@SpringBootTest
@ActiveProfiles("local")
class JsonApiAdviceTests {

    @Autowired WebApplicationContext context;
    @MockitoBean CommunityService communityService;
    @MockitoBean CartService cartService;
    @MockitoBean NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MemberDetails customer() {
        return new MemberDetails(7L, "user@cakeshop.local", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    /** JSON 컨트롤러의 업무 예외는 HTML 오류 페이지가 아니라 {@code {code,message}} 로 나간다. */
    @Test
    void restController오류는_JSON으로_나간다() throws Exception {
        when(communityService.toggleLike(anyLong(), anyLong()))
            .thenThrow(new com.cakeshop.global.error.BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        mockMvc.perform(post("/community/api/posts/{id}/like", 999)
                .with(user(customer())).with(csrf()))
            .andExpect(status().is(CommunityErrorCode.POST_NOT_FOUND.status()))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(CommunityErrorCode.POST_NOT_FOUND.code()))
            .andExpect(jsonPath("$.message").value(CommunityErrorCode.POST_NOT_FOUND.message()));
    }

    /** 같은 예외라도 화면 컨트롤러에서는 오류 페이지를 렌더한다. */
    @Test
    void controller오류는_HTML_오류페이지로_나간다() throws Exception {
        when(communityService.getPostDetail(anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean()))
            .thenThrow(new com.cakeshop.global.error.BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        mockMvc.perform(get("/community/{id}", 999))
            .andExpect(status().is(CommunityErrorCode.POST_NOT_FOUND.status()))
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            // 404는 전용 페이지다(GlobalExceptionHandler.viewFor).
            .andExpect(view().name("error/404"));
    }

    /** 헤더용 공통 모델은 화면 요청에서만 조회한다 — JSON 요청에서는 쿼리가 돌지 않는다. */
    @Test
    void 헤더_공통모델은_JSON_요청에서_조회하지_않는다() throws Exception {
        when(communityService.getPostSlice(null, null, null))
            .thenReturn(new com.cakeshop.domain.community.dto.view.PostSliceView(List.of(), false, null));

        mockMvc.perform(get("/community/api/posts").with(user(customer())))
            .andExpect(status().isOk());

        verify(cartService, never()).getTotalQuantity(anyLong());
        verify(notificationService, never()).countUnread(anyLong());
    }
}
