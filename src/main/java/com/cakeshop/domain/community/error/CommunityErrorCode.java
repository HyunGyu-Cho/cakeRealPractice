package com.cakeshop.domain.community.error;

import com.cakeshop.global.error.ErrorCode;

public enum CommunityErrorCode implements ErrorCode {

    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403),
    // 삭제된 글은 존재를 노출하지 않도록 404로 응답한다.
    POST_NOT_FOUND("COMMUNITY_002", "게시글을 찾을 수 없습니다.", 404),
    CATEGORY_NOT_FOUND("COMMUNITY_003", "선택한 분류를 찾을 수 없습니다.", 400),
    PARENT_COMMENT_NOT_FOUND("COMMUNITY_004", "답글을 달 수 없는 댓글입니다.", 400),
    INVALID_STATUS_CHANGE("COMMUNITY_005", "현재 상태에서는 처리할 수 없습니다.", 400),
    NOT_AUTHOR("COMMUNITY_006", "본인이 작성한 게시물만 처리할 수 있습니다.", 403),
    COMMENT_NOT_FOUND("COMMUNITY_007", "댓글을 찾을 수 없습니다.", 404),
    ALREADY_REPORTED("COMMUNITY_008", "이미 신고한 게시글입니다.", 400),
    CANNOT_REPORT_OWN_POST("COMMUNITY_009", "본인이 작성한 글은 신고할 수 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CommunityErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
