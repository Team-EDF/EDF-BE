package com.edf.teamedf.domain.user.query.dto.user;

import com.edf.teamedf.domain.user.command.domain.User;

import java.time.LocalDateTime;

public record UserSummaryResponse(
        Long userId,
        String name,
        String email,
        String phone,
        User.Role role,
        User.Gender gender,
        Boolean enabled,
        String profileImageUrl,
        String bio,
        LocalDateTime createdAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getGender(),
                user.getEnabled(),
                user.getProfileImageUrl(),
                user.getBio(),
                user.getCreatedAt()
        );
    }
}
