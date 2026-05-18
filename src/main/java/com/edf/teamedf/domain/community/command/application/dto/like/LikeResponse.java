package com.edf.teamedf.domain.community.command.application.dto.like;

public record LikeResponse(
        boolean liked,
        int likeCount
) {}
