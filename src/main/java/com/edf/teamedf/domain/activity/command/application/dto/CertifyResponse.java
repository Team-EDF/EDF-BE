package com.edf.teamedf.domain.activity.command.application.dto;

import com.edf.teamedf.domain.activity.command.domain.EcoActivity;

/**
 * 친환경 활동 인증 결과.
 *
 * <p>프론트엔드 api/upload.js 의 기대 응답 스키마와 1:1로 맞춘다.
 * 기존 필드는 유지하면서 캐릭터 진화 연출에 필요한 이전/현재 레벨을 함께 내려준다.</p>
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
        Long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp
) {

    public static CertifyResponse of(
            EcoActivity activity,
            Float totalSavedCarbon,
            Long totalPoints,
            int previousLevel,
            int level
    ) {
        return new CertifyResponse(
                true,
                activity.getCategory().getDisplayName() + " 인증이 완료되었습니다.",
                activity.getActivityId(),
                activity.getCategory().name(),
                activity.getSavedCarbon(),
                activity.getPointsEarned(),
                activity.getDetectionName(),
                totalSavedCarbon,
                totalPoints,
                previousLevel,
                level,
                level > previousLevel
        );
    }
}
