package com.edf.teamedf.domain.community.command.application.dto.block;

import com.edf.teamedf.domain.community.command.domain.UserBlock;

import java.time.LocalDateTime;

public record BlockedUserResponse(
        Long userId,
        String name,
        String profileImageUrl,
        LocalDateTime blockedAt
) {

    public static BlockedUserResponse from(UserBlock block) {
        return new BlockedUserResponse(
                block.getBlocked().getUserId(),
                block.getBlocked().getName(),
                block.getBlocked().getProfileImageUrl(),
                block.getCreatedAt()
        );
    }
}
