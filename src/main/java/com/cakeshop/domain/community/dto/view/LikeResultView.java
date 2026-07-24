package com.cakeshop.domain.community.dto.view;

/** 좋아요 토글 API 응답 — 버튼 상태와 카운트를 화면이 즉시 갱신한다. */
public record LikeResultView(
    boolean liked,
    long likeCount
) {
}
