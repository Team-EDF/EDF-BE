package com.edf.teamedf.domain.activity.command.application.dto;

import com.edf.teamedf.domain.activity.command.domain.EcoActivity;

import java.time.LocalDateTime;

/** 인증 기록 단건 응답. */
public record EcoActivityResponse(
        Long activityId,
        String category,
        String categoryName,
        String imageUrl,
        String comment,
        Float savedCarbon,
        Integer pointsEarned,
        String detectionName,
        String status,
        LocalDateTime createdAt
) {

    public static EcoActivityResponse from(EcoActivity a) {
        return new EcoActivityResponse(
                a.getActivityId(),
                a.getCategory().name(),
                a.getCategory().getDisplayName(),
                a.getImageUrl(),
                a.getComment(),
                a.getSavedCarbon(),
                a.getPointsEarned(),
                a.getDetectionName(),
                a.getStatus().name(),
                a.getCreatedAt()
        );
    }
}
