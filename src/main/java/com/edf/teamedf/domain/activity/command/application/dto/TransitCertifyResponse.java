package com.edf.teamedf.domain.activity.command.application.dto;

/**
 * GPS 기반 이동수단 탄소 절감 인증 결과.
 *
 * <p>프론트엔드 api/transit.js 의 기대 응답 스키마와 1:1로 맞춘다.
 * 활동 결과와 함께 누적 수치 및 캐릭터 진화 여부를 내려준다.</p>
 */
public record TransitCertifyResponse(
        boolean success,
        Float savedCarbon,
        Integer pointsEarned,
        Float totalSavedCarbon,
        Long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp
) {

    public static TransitCertifyResponse of(
            Float savedCarbon,
            Integer pointsEarned,
            Float totalSavedCarbon,
            Long totalPoints,
            int previousLevel,
            int level
    ) {
        return new TransitCertifyResponse(
                true,
                savedCarbon,
                pointsEarned,
                totalSavedCarbon,
                totalPoints,
                previousLevel,
                level,
                level > previousLevel
        );
    }
}
