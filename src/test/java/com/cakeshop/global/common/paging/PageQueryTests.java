package com.cakeshop.global.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code fragments/common/pagination.html} 의 {@code extraQuery} 계약을 고정한다 —
 * 값이 있으면 맨 앞 {@code &} 를 포함하고, 없으면 빈 문자열이다.
 *
 * <p>이 빌더 이전에는 같은 코드가 7개 컨트롤러에 복사돼 있었다. 한 곳만 인코딩을 빠뜨려도
 * 한글 검색어로 페이지를 넘길 때 조건이 조용히 깨진다.
 */
class PageQueryTests {

    @Test
    void 값이_없으면_빈_문자열이다() {
        assertThat(PageQuery.of().toQueryString()).isEmpty();
        assertThat(PageQuery.of().add("status", (String) null).toQueryString()).isEmpty();
        assertThat(PageQuery.of().add("status", "").toQueryString()).isEmpty();
        assertThat(PageQuery.of().add("status", "   ").toQueryString()).isEmpty();
    }

    @Test
    void 맨_앞에_앰퍼샌드를_붙이고_추가_순서를_지킨다() {
        String query = PageQuery.of()
            .add("status", "ACTIVE")
            .add("category", "REVIEW")
            .toQueryString();

        assertThat(query).isEqualTo("&status=ACTIVE&category=REVIEW");
    }

    @Test
    void 한글_검색어는_UTF8로_인코딩한다() {
        String query = PageQuery.of().add("keyword", "초코케이크").toQueryString();

        assertThat(query).startsWith("&keyword=%");
        assertThat(query).doesNotContain("초코케이크");
    }

    @Test
    void 쿼리스트링을_깨뜨리는_문자도_인코딩한다() {
        String query = PageQuery.of().add("keyword", "a&b=c").toQueryString();

        // 값 안의 & 와 = 가 파라미터 구분자로 새어나가면 안 된다.
        assertThat(query).isEqualTo("&keyword=a%26b%3Dc");
    }

    @Test
    void 숫자와_불리언도_담을_수_있다() {
        String query = PageQuery.of()
            .add("minPrice", 1000)
            .add("pickupToday", "true")
            .toQueryString();

        assertThat(query).isEqualTo("&minPrice=1000&pickupToday=true");
    }

    @Test
    void 값은_앞뒤_공백을_제거한다() {
        assertThat(PageQuery.of().add("keyword", "  케이크  ").toQueryString())
            .isEqualTo(PageQuery.of().add("keyword", "케이크").toQueryString());
    }

    @Test
    void 같은_키를_다시_넣으면_나중_값이_이긴다() {
        assertThat(PageQuery.of().add("status", "ACTIVE").add("status", "HIDDEN").toQueryString())
            .isEqualTo("&status=HIDDEN");
    }

    @Test
    void toString은_쿼리스트링과_같다() {
        PageQuery query = PageQuery.of().add("sort", "LATEST");

        assertThat(query.toString()).isEqualTo(query.toQueryString());
    }
}
