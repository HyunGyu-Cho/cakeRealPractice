package com.cakeshop.domain.review.dto.form;

import com.cakeshop.domain.review.entity.ReviewStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/** 관리자 후기 검색 조건. 빈 문자열은 "조건 없음"으로 정규화해 mapper에 넘긴다. */
@Getter
@Setter
public class AdminReviewSearchForm {

    private String keyword;   // 상품명 부분 일치
    private String status;    // VISIBLE / HIDDEN
    private Integer rating;   // 종합 평점 정확히 일치
    private Integer page = 1;

    public String getNormalizedKeyword() {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    /** 알 수 없는 값은 조건 없음으로 떨군다 — 화면에서 온 문자열을 그대로 SQL에 넣지 않는다. */
    public String getNormalizedStatus() {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        for (ReviewStatus value : ReviewStatus.values()) {
            if (value.name().equals(status)) {
                return value.name();
            }
        }
        return null;
    }

    public Integer getNormalizedRating() {
        return rating != null && rating >= 1 && rating <= 5 ? rating : null;
    }
}
