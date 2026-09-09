package com.edf.teamedf.domain.community.command.application.dto.post;

import com.edf.teamedf.domain.community.command.domain.Post;

import java.time.LocalDateTime;

public record PostResponse(
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
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PostResponse from(Post post) {
        return from(post, 0L, false, null);
    }

    public static PostResponse from(Post post, long commentCount, boolean likedByMe, Long viewerId) {
        Long authorId = post.getUser() == null ? null : post.getUser().getUserId();
        return new PostResponse(
                post.getPostId(),
                authorId,
                post.getUser() == null ? "알 수 없음" : post.getUser().getName(),
                post.getUser() == null ? null : post.getUser().getProfileImageUrl(),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getLikeCount(),
                commentCount,
                likedByMe,
                viewerId != null && viewerId.equals(authorId),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
