package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.community.dto.form.PostCreateForm;
import com.cakeshop.domain.community.entity.PostCategory;
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
class CommunityControllerTests {

    @Mock
    private CommunityService communityService;

    private CommunityController controller;

    @BeforeEach
    void setUp() {
        controller = new CommunityController(communityService);
    }

    @Test
    void listFetchesActiveCategoriesOnlyOnce() {
        List<PostCategory> categories = List.of(category("FREE"));
        ExtendedModelMap model = new ExtendedModelMap();
        when(communityService.getActiveCategories()).thenReturn(categories);
        when(communityService.normalizeCategory("FREE", categories)).thenReturn("FREE");

        String view = controller.list("FREE", 3, model);

        assertThat(view).isEqualTo("customer/community/list");
        assertThat(model.get("currentCategory")).isEqualTo("FREE");
        assertThat(model.get("categories")).isSameAs(categories);
        assertThat(model.get("errorMessage")).isNull();
        // 검증과 화면 출력이 같은 목록을 공유하므로 요청당 조회는 1회여야 한다.
        verify(communityService, times(1)).getActiveCategories();
    }

    @Test
    void listShowsErrorMessageForUnknownCategoryAndFallsBackToAllPosts() {
        List<PostCategory> categories = List.of(category("FREE"));
        ExtendedModelMap model = new ExtendedModelMap();
        when(communityService.getActiveCategories()).thenReturn(categories);
        when(communityService.normalizeCategory("WRONG", categories)).thenReturn(null);

        controller.list("WRONG", null, model);

        assertThat(model.get("currentCategory")).isNull();
        assertThat(model.get("errorMessage")).isEqualTo("잘못된 카테고리 양식입니다.");
        verify(communityService, times(1)).getActiveCategories();
    }

    @Test
    void listWithoutCategoryShowsAllPostsWithoutError() {
        List<PostCategory> categories = List.of(category("FREE"));
        ExtendedModelMap model = new ExtendedModelMap();
        when(communityService.getActiveCategories()).thenReturn(categories);
        when(communityService.normalizeCategory(null, categories)).thenReturn(null);

        controller.list(null, null, model);

        assertThat(model.get("currentCategory")).isNull();
        assertThat(model.get("errorMessage")).isNull();
        verify(communityService, times(1)).getActiveCategories();
    }

    @Test
    void invalidCreateRendersFormWithoutCallingService() {
        PostCreateForm form = new PostCreateForm();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "form");
        errors.addError(new FieldError("form", "title", "제목을 입력해 주세요."));
        when(communityService.getActiveCategories()).thenReturn(List.of(new PostCategory()));

        String view = controller.create(
            member(1L), form, errors, new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("customer/community/form");
        verify(communityService, never()).createPost(any(Long.class), any(PostCreateForm.class));
    }

    @Test
    void validCreateRedirectsToCreatedPostWithFlashMessage() {
        PostCreateForm form = new PostCreateForm();
        form.setCategoryCode("NOTICE");
        form.setTitle("제목");
        form.setContent("내용");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "form");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(communityService.createPost(1L, form)).thenReturn(10L);

        String view = controller.create(
            member(1L), form, errors, new ExtendedModelMap(), redirect);

        assertThat(view).isEqualTo("redirect:/community/10");
        assertThat(redirect.getFlashAttributes().get("successMessage"))
            .isEqualTo("게시글이 등록되었습니다.");
    }

    private PostCategory category(String code) {
        PostCategory category = new PostCategory();
        category.setCode(code);
        return category;
    }

    private MemberDetails member(long id) {
        return new MemberDetails(id, "member@example.com", "password", List.of());
    }
}
