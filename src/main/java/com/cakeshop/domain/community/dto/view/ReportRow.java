package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 관리자 신고 내역 mapper 전용 프로젝션. */
@Getter
@Setter
public class ReportRow {

    private Long id;
    private Long reporterId;
    private String reason;
    private String status;
    private LocalDateTime createdAt;

}
