package com.edf.teamedf.domain.activity.command.application.dto;

import java.util.List;

/**
 * GPS 기반 이동수단 탄소 절감 인증 요청.
 *
 * <p>서버는 route(시각이 있는 경로)로 이동수단 주장과 이동 거리를 직접 검증·계산한다.
 * savedCarbon / pointsEarned / distanceKm 은 앱 화면용 값이라 서버는 믿지 않고 다시 계산한다
 * (예전 앱과의 호환을 위해 필드는 남겨 둔다).</p>
 *
 * <p>RoutePoint: timestamp = 위치를 받은 시각(epoch ms), speed = 기기가 준 속도(m/s, 참고용),
 * accuracy = 위치 정확도(m), segmentBreak = 일시정지 후 재개한 첫 지점(그 앞과의 이동은 관측하지 못함).</p>
 */
public record TransitCertifyRequest(
        String mode,
        Float distanceKm,
        Integer durationSec,
        Float savedCarbon,
        Integer pointsEarned,
        List<RoutePoint> route
) {
    public record RoutePoint(
            Double latitude,
            Double longitude,
            Long timestamp,
            Float speed,
            Float accuracy,
            Boolean segmentBreak
    ) {
    }
}
