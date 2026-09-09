package com.edf.teamedf.domain.activity.command.application.dto;

import com.edf.teamedf.domain.activity.command.domain.EcoActivity;

/**
 * 친환경 활동 인증 결과.
 *
 * <p>프론트엔드 api/upload.js 의 기대 응답 스키마와 1:1로 맞춘다.
 * (success, message, savedCarbon, pointsEarned, detectionName)</p>
 */
public record CertifyResponse(
        boolean success,
        String message,
        Long activityId,
        String category,
        Float savedCarbon,
        Integer pointsEarned,
        String detectionName,
        Float totalSavedCarbon,
        Long totalPoints
) {

    public static CertifyResponse of(EcoActivity activity, Float totalSavedCarbon, Long totalPoints) {
        return new CertifyResponse(
                true,
                activity.getCategory().getDisplayName() + " 인증이 완료되었습니다.",
                activity.getActivityId(),
                activity.getCategory().name(),
                activity.getSavedCarbon(),
                activity.getPointsEarned(),
                activity.getDetectionName(),
                totalSavedCarbon,
                totalPoints
        );
    }
}
