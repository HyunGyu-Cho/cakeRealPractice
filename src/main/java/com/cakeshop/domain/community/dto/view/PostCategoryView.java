package com.cakeshop.domain.community.dto.view;

/** 카테고리 필터·작성 폼 선택지에 필요한 화면 모델. 저장용 id는 필요한 곳에서 code로 따로 조회한다. */
public record PostCategoryView(
    String code,
    String name
) {
}
