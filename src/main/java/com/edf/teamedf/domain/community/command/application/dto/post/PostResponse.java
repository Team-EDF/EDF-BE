package com.edf.teamedf.domain.community.command.application.dto.post;

import com.edf.teamedf.domain.community.command.domain.Post;

import java.time.LocalDateTime;

public record PostResponse(
        Long postId,
        Long userId,
        String authorName,
        String title,
        String content,
        int viewCount,
        int likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getPostId(),
                post.getUser().getUserId(),
                post.getUser().getName(),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
