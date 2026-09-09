package com.edf.teamedf.domain.user.command.application.dto.profile;

import com.edf.teamedf.domain.user.command.domain.User;

import java.time.LocalDateTime;

/** 로그인한 본인 정보 + 활동 카운트. */
public record MeResponse(
        Long userId,
        String uuid,
        String email,
        String name,
        String bio,
        String phone,
        String profileImageUrl,
        User.Role role,
        User.Gender gender,
        String address,
        String addressDetail,
        String zipCode,
        String provider,
        LocalDateTime createdAt,
        Stats stats
) {

    public record Stats(
            long postCount,
            long commentCount,
            long likedPostCount,
            long activityCount,
            float totalSavedCarbon,
            long totalPoints,
            int level
    ) {
    }

    public static MeResponse of(User user, Stats stats) {
        return new MeResponse(
                user.getUserId(),
                user.getUuid(),
                user.getEmail(),
                user.getName(),
                user.getBio(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.getGender(),
                user.getAddress(),
                user.getAddressDetail(),
                user.getZipCode(),
                user.getProvider(),
                user.getCreatedAt(),
                stats
        );
    }
}
