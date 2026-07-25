package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.community.dto.form.PostBlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class CommunityAdminControllerTests {

    @Mock
    private CommunityAdminService communityAdminService;

    @Mock
    private CommunityService communityService;

    @Mock
    private AdminPostDetailView postDetail;

    private CommunityAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CommunityAdminController(communityAdminService, communityService);
    }

    @Test
    void invalidBlockRendersDetailWithoutCallingService() {
        PostBlockForm form = new PostBlockForm();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "blockForm");
        errors.addError(new FieldError("blockForm", "reason", "제재 사유를 입력해 주세요."));
        when(communityAdminService.getPostDetail(10L)).thenReturn(postDetail);

        String view = controller.block(
            10L, member(99L), form, errors, new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("admin/community/detail");
        verify(communityAdminService, never()).blockPost(99L, 10L, form);
    }

    @Test
    void validBlockRedirectsWithFlashMessage() {
        PostBlockForm form = new PostBlockForm();
        form.setReason("광고");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "blockForm");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.block(
            10L, member(99L), form, errors, new ExtendedModelMap(), redirect);

        assertThat(view).isEqualTo("redirect:/admin/community/10");
        assertThat(redirect.getFlashAttributes().get("successMessage"))
            .isEqualTo("게시글을 제재했습니다.");
        verify(communityAdminService).blockPost(99L, 10L, form);
    }

    private MemberDetails member(long id) {
        return new MemberDetails(id, "admin@example.com", "password", List.of());
    }
}
