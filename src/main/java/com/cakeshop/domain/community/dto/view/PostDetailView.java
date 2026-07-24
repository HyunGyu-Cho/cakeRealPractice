package com.cakeshop.domain.community.dto.view;

import java.util.List;

/** 상세 화면 읽기 모델. mine=true면 작성자 본인이라 수정·삭제 버튼을 노출한다. */
public record PostDetailView(
    Long id,
    String categoryCode,
    String categoryName,
    String title,
    String content,
    String nickname,
    long viewCount,
    long likeCount,
    boolean likedByMe,
    boolean mine,
    String createdDate,
    List<CommentView> comments
) {
    /** 삭제 마스킹 자리는 세지 않는다. */
    public long commentCount() {
        return comments.stream().filter(comment -> !comment.deleted()).count();
    }
}
