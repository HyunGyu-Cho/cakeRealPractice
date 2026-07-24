package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 목록 조회 mapper 전용 프로젝션. 닉네임은 member 도메인 소유라 여기에 없다 —
 * service가 memberId로 MemberService에서 받아 {@link PostSummaryView}로 완성한다.
 */
@Getter
@Setter
public class PostSummaryRow {

    private Long id;
    private Long memberId;
    private String categoryCode;
    private String categoryName;
    private String title;
    private long likeCount;
    private long commentCount;
    private LocalDateTime createdAt;

}
