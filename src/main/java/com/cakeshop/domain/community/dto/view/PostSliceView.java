package com.cakeshop.domain.community.dto.view;

import java.util.List;

/**
 * 무한스크롤 응답. 페이지 번호 방식(PageResult)과 달리 전체 카운트가 없고,
 * 마지막으로 내려준 글 id(nextCursor)를 다음 요청의 기준점으로 쓴다(keyset 페이징).
 */
public record PostSliceView(
    List<PostSummaryView> posts,
    boolean hasNext,
    Long nextCursor
) {
}
