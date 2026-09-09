package com.edf.teamedf.domain.community.command.application.dto.comment;

import com.edf.teamedf.domain.community.command.domain.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long userId,
        String authorName,
        String authorProfileImageUrl,
        Long parentCommentId,
        String content,
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse from(Comment comment) {
        return from(comment, null);
    }

    public static CommentResponse from(Comment comment, Long viewerId) {
        Long authorId = comment.getUser() == null ? null : comment.getUser().getUserId();
        return new CommentResponse(
                comment.getCommentId(),
                authorId,
                comment.getUser() == null ? "알 수 없음" : comment.getUser().getName(),
                comment.getUser() == null ? null : comment.getUser().getProfileImageUrl(),
                comment.getParent() != null ? comment.getParent().getCommentId() : null,
                comment.getContent(),
                viewerId != null && viewerId.equals(authorId),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
