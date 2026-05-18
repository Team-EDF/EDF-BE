package com.edf.teamedf.domain.community.command.application.dto.post;

import com.edf.teamedf.domain.community.command.domain.Post;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        Long postId,
        String authorName,
        String title,
        int viewCount,
        int likeCount,
        LocalDateTime createdAt
) {
    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getPostId(),
                post.getUser().getName(),
                post.getTitle(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt()
        );
    }
}
