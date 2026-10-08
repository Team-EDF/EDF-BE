package com.edf.teamedf.domain.activity.command.application.dto;

/**
 * GPS 기반 이동수단 탄소 절감 인증 결과.
 *
 * <p>프론트엔드 api/transit.js 의 기대 응답 스키마와 맞춘다.
 * 서버가 경로로 검증해서 인정하지 않은 이동은 {@code success=false} + {@code message} 로 돌려주고 기록하지 않는다.
 * 인정한 경우 절감량·포인트·거리는 서버가 경로로 다시 계산한 값이다.</p>
 *
 * @param message      안내 문구 (인정 시: 확인된 이동 형태, 거절 시: 거절 사유)
 * @param detectedMode 속도로 본 이동 형태: WALK | VEHICLE | SUBWAY | UNKNOWN
 * @param distanceKm   서버가 인정한 이동 거리(km)
 */
public record TransitCertifyResponse(
        boolean success,
        Float savedCarbon,
        Integer pointsEarned,
        Float totalSavedCarbon,
        Long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp,
        String message,
        String detectedMode,
        Float distanceKm
) {

    public static TransitCertifyResponse of(
            Float savedCarbon,
            Integer pointsEarned,
            Float totalSavedCarbon,
            Long totalPoints,
            int previousLevel,
            int level,
            String message,
            String detectedMode,
            Float distanceKm
    ) {
        return new TransitCertifyResponse(
                true,
                savedCarbon,
                pointsEarned,
                totalSavedCarbon,
                totalPoints,
                previousLevel,
                level,
                level > previousLevel,
                message,
                detectedMode,
                distanceKm
        );
    }

    /** 검증에서 인정되지 않은 이동 (기록·포인트 없음). */
    public static TransitCertifyResponse rejected(
            String message,
            String detectedMode,
            Float totalSavedCarbon,
            Long totalPoints,
            int level
    ) {
        return new TransitCertifyResponse(
                false, 0f, 0, totalSavedCarbon, totalPoints, level, level, false, message, detectedMode, 0f);
    }
}
