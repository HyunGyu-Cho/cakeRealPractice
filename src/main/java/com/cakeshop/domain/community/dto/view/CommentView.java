package com.cakeshop.domain.community.dto.view;

/**
 * 댓글 한 건 표시용.
 * reply=true면 부모 댓글에 달린 답글이라 들여쓴다.
 * mine=true면 본인 댓글이라 삭제 버튼을 노출한다.
 * deleted=true면 답글 유지를 위해 남긴 자리로, 내용이 마스킹된 상태다.
 */
public record CommentView(
    Long id,
    String nickname,
    String content,
    String createdDate,
    boolean reply,
    boolean mine,
    boolean deleted
) {
}
