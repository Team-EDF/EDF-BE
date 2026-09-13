package com.edf.teamedf.domain.activity.command.application.dto;

import java.util.List;

/**
 * GPS 기반 이동수단 탄소 절감 인증 요청.
 *
 * <p>프론트엔드(api/transit.js)가 GPS로 이미 계산한 거리/절감량/포인트를 그대로 전달한다.
 * route 는 경로 재조회용으로 저장하지 않는 선택 항목이다.</p>
 */
public record TransitCertifyRequest(
        String mode,
        Float distanceKm,
        Integer durationSec,
        Float savedCarbon,
        Integer pointsEarned,
        List<RoutePoint> route
) {
    public record RoutePoint(Double latitude, Double longitude) {
    }
}
