package com.edf.teamedf.domain.community.command.application.dto.comment;

import com.edf.teamedf.domain.community.command.domain.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long userId,
        String authorName,
        Long parentCommentId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getUser().getUserId(),
                comment.getUser().getName(),
                comment.getParent() != null ? comment.getParent().getCommentId() : null,
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
