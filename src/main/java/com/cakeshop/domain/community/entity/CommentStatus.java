package com.cakeshop.domain.community.entity;

// 저장값은 enum 이름(V2 SQL의 chk_comments_status와 동일 집합), 한글 라벨은 화면 전용
public enum CommentStatus {

    ACTIVE("정상"),
    DELETED("삭제됨");

    private final String label;

    CommentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
