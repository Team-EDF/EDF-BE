package com.edf.teamedf.domain.community.command.application.dto.post;

import com.edf.teamedf.domain.community.command.domain.Post;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        Long postId,
        Long userId,
        String authorName,
        String authorProfileImageUrl,
        String title,
        String content,
        int viewCount,
        int likeCount,
        long commentCount,
        boolean likedByMe,
        LocalDateTime createdAt
) {

    public static PostSummaryResponse from(Post post) {
        return from(post, 0L, false);
    }

    public static PostSummaryResponse from(Post post, long commentCount, boolean likedByMe) {
        return new PostSummaryResponse(
                post.getPostId(),
                post.getUser() == null ? null : post.getUser().getUserId(),
                post.getUser() == null ? "알 수 없음" : post.getUser().getName(),
                post.getUser() == null ? null : post.getUser().getProfileImageUrl(),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getLikeCount(),
                commentCount,
                likedByMe,
                post.getCreatedAt()
        );
    }
}
