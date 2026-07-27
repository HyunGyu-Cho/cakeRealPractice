package com.cakeshop.global.common.paging;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.util.UriUtils;

/**
 * 페이지 링크에 유지할 쿼리 문자열을 만든다 — {@code fragments/common/pagination.html} 의
 * {@code extraQuery} 입력이다.
 *
 * <p>뷰는 공통 프래그먼트로 이미 공유돼 있었는데 그 <b>입력을 만드는 코드가 7개 컨트롤러에
 * 복사</b>돼 있었다(모두 {@code StringBuilder} + {@code UriUtils.encodeQueryParam} + 수동 공백검사).
 * 한글 검색어 인코딩을 한 곳만 빠뜨려도 페이지를 넘길 때 조건이 조용히 깨진다.
 *
 * <p>프래그먼트 계약을 그대로 지킨다 — 값이 있으면 {@code "&k=v&k2=v2"}(맨 앞 {@code &} 포함),
 * 없으면 빈 문자열. {@code page} 는 프래그먼트가 직접 붙이므로 여기 담지 않는다.
 *
 * <pre>
 * String extraQuery = PageQuery.of()
 *     .add("status", status)
 *     .add("keyword", keyword)
 *     .toQueryString();
 * </pre>
 */
public final class PageQuery {

    private final Map<String, String> params = new LinkedHashMap<>();

    private PageQuery() {
    }

    public static PageQuery of() {
        return new PageQuery();
    }

    /** {@code null}·빈 값은 건너뛴다. 같은 키를 다시 넣으면 나중 값이 이긴다. */
    public PageQuery add(String name, String value) {
        if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
            params.put(name, value.trim());
        }
        return this;
    }

    public PageQuery add(String name, Object value) {
        return add(name, value == null ? null : String.valueOf(value));
    }

    /** 담긴 값이 없으면 빈 문자열. 값은 UTF-8로 인코딩한다(한글 검색어). */
    public String toQueryString() {
        StringBuilder query = new StringBuilder();
        params.forEach((name, value) -> query.append('&').append(name).append('=')
            .append(UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)));
        return query.toString();
    }

    @Override
    public String toString() {
        return toQueryString();
    }
}
