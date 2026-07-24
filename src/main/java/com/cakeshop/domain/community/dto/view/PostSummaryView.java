package com.cakeshop.domain.community.dto.view;

/** 목록 한 줄 표시용 읽기 모델. 화면·API(JSON) 공용이라 날짜는 표시 형식 문자열로 확정해 담는다. */
public record PostSummaryView(
    Long id,
    String categoryCode,
    String categoryName,
    String title,
    String nickname,
    long likeCount,
    long commentCount,
    String createdDate
) {
}
