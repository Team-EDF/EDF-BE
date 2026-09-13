package com.edf.teamedf.domain.activity.command.application.dto;

/**
 * GPS 기반 이동수단 탄소 절감 인증 결과.
 *
 * <p>프론트엔드 api/transit.js 의 기대 응답 스키마와 1:1로 맞춘다.
 * (success, savedCarbon, pointsEarned)</p>
 */
public record TransitCertifyResponse(
        boolean success,
        Float savedCarbon,
        Integer pointsEarned
) {

    public static TransitCertifyResponse of(Float savedCarbon, Integer pointsEarned) {
        return new TransitCertifyResponse(true, savedCarbon, pointsEarned);
    }
}
