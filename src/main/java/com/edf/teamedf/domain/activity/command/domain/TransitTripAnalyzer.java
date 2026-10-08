package com.edf.teamedf.domain.activity.command.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GPS 이동 경로의 속도 패턴으로 이동수단 주장(도보/대중교통)을 검증한다.
 *
 * <p>지금까지는 사용자가 앱에서 이동수단을 직접 고르고 서버는 앱이 계산한 절감량·포인트를 그대로 믿었다.
 * 그래서 걸으면서 "대중교통"을 눌러도 인정됐다. 이 클래스는 경로의 시각·위치로 속도를 다시 계산해서
 * 주장과 맞지 않는 이동을 걸러내고, 인정하는 거리도 서버가 직접 계산한다.</p>
 *
 * <h3>판정 근거 (속도 기반)</h3>
 * <ul>
 *   <li>도보와 차량은 속도 분포로 구분된다 (도보 약 0.1~15km/h, 시내 버스 구간은 그보다 빠름).
 *       연구들은 속도 외에 정차 비율·가속도 변동으로 버스와 승용차를 구분한다.</li>
 *   <li>속도만으로는 <b>버스와 자가용을 구분할 수 없다</b>. 그래서 이 검사는 "대중교통이라고 주장했는데
 *       걷는 속도뿐이다", "도보라고 했는데 차량 속도다" 같은 명백한 불일치를 거절하는 용도다.</li>
 *   <li>지하철은 지하에서 GPS가 끊겼다가 한참 뒤 위치가 점프한다. 끊긴 시간 동안의 이동 속도가
 *       지하철 범위(15~120km/h)이고 거리가 1km 이상이면 지하철 구간으로 인정한다.
 *       (서울 지하철 표정속도 평균은 약 34km/h)</li>
 *   <li>앱에서 일시정지 후 재개한 지점(segmentBreak)은 이동을 관측하지 못한 구간이라 거리로 세지 않는다.
 *       (일시정지 중 자차로 이동한 뒤 재개해서 지하철 점프로 위장하는 것을 막는다)</li>
 * </ul>
 *
 * <p>한계: 위치 조작 앱(모의 위치)이나 이동 기록 요청을 직접 만들어 보내는 경우는 막지 못한다.
 * 같은 경로의 재전송은 마지막 위치 시각이 {@link #MAX_AGE_MS} 이내여야 한다는 조건으로만 일부 막는다.</p>
 */
public final class TransitTripAnalyzer {

    public static final double MIN_DISTANCE_KM = 0.3;
    /** 이 정확도(m)보다 나쁜 위치는 버린다 (호출하는 쪽에서 걸러서 넘긴다). */
    public static final double MAX_ACCURACY_M = 150;
    /** 이 거리(m) 미만의 이동은 GPS 흔들림으로 보고 거리로 세지 않는다 (앱의 거리 계산과 같은 값). */
    static final double JITTER_M = 3;
    /** 이 속도(km/h) 이상이어야 "이동 중"으로 센다. */
    static final double MOVING_KMH = 3;
    /** 이 속도(km/h) 이상이면 차량 속도 구간이다. (달리기는 보통 이보다 느리다) */
    static final double VEHICLE_KMH = 12;
    /** 위치가 이 시간(초) 이상 끊기고 이 거리(m) 이상 점프하면 "GPS 끊김 후 점프"로 본다. */
    static final double GAP_SEC = 40;
    static final double JUMP_MIN_M = 300;
    /** 점프 구간의 이동 속도 범위(km/h): 지하철/터널. 이 밖이면 인정하지 않는다. */
    static final double JUMP_MIN_KMH = 15;
    static final double JUMP_MAX_KMH = 120;
    /** 이 속도(km/h)를 넘는 순간 이동은 위치 오류로 본다. */
    static final double TELEPORT_KMH = 200;
    /** 지하철 구간으로 인정하는 점프 거리 합계(km). */
    static final double SUBWAY_JUMP_MIN_KM = 1.0;
    /** 대중교통(버스) 인정: 이동 시간 중 차량 속도 구간 비율과 최소 차량 이동 시간(초). */
    static final double VEHICLE_SHARE_MIN = 0.5;
    static final double VEHICLE_MIN_SEC = 45;
    /** 대중교통 인정 상한: 속도 상위 5%(p95)가 이 값(km/h)을 넘으면 시내 대중교통으로 보지 않는다. */
    static final double TRANSIT_MAX_P95_KMH = 130;
    /** 도보 인정 상한: p95 속도 15km/h, 차량 속도 구간 비율 30% 미만. */
    static final double WALK_MAX_P95_KMH = 15;
    static final double WALK_MAX_VEHICLE_SHARE = 0.3;
    /** 경로의 마지막 위치 시각이 지금보다 이만큼(ms) 이상 오래됐으면 인정하지 않는다. */
    public static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final long FUTURE_TOLERANCE_MS = 5 * 60 * 1000L;
    private static final int MIN_POINTS = 3;

    private TransitTripAnalyzer() {
    }

    /** 경로의 점 하나. timestamp는 위치를 받은 시각(epoch ms). */
    public record Point(double latitude, double longitude, long timestamp, boolean segmentBreak) {
    }

    /** 경로를 구간으로 나눠 계산한 통계. */
    public record Stats(
            int usablePoints,
            double regularKm,       // 정상 구간(이동 중 + 정지 흔들림 제외)의 거리 합
            double jumpKm,          // 인정 가능한 GPS 끊김 점프 거리 합
            int jumpCount,
            double movingSec,       // 이동 중(3km/h 이상) 시간
            double vehicleSec,      // 그중 차량 속도(12km/h 이상) 시간
            double p95Kmh,          // 이동 중 구간의 시간 가중 상위 5% 속도
            double maxKmh,
            boolean teleport        // 200km/h를 넘는 순간 이동이 있었는지
    ) {
        public double vehicleShare() {
            return movingSec <= 0 ? 0 : vehicleSec / movingSec;
        }
    }

    /**
     * @param ok           인정 여부
     * @param message      사용자에게 보여 줄 안내 문구
     * @param detectedMode 속도로 본 이동 형태: WALK | VEHICLE | SUBWAY | UNKNOWN
     * @param creditedKm   인정하는 이동 거리(km). 거절이면 0
     */
    public record Verdict(boolean ok, String message, String detectedMode, double creditedKm) {
        static Verdict ok(String message, String mode, double km) {
            return new Verdict(true, message, mode, km);
        }

        static Verdict rejected(String message, String mode) {
            return new Verdict(false, message, mode, 0);
        }
    }

    // ------------------------------------------------------------------ 계산

    public static Stats analyze(List<Point> raw) {
        List<Point> points = new ArrayList<>();
        for (Point p : raw) {
            if (points.isEmpty() || p.timestamp() > points.get(points.size() - 1).timestamp()) {
                points.add(p);
            }
        }

        double regularM = 0;
        double jumpM = 0;
        int jumps = 0;
        double movingSec = 0;
        double vehicleSec = 0;
        double maxKmh = 0;
        boolean teleport = false;
        List<double[]> movingSegments = new ArrayList<>();   // {speedKmh, seconds}

        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1);
            Point b = points.get(i);
            if (b.segmentBreak()) {
                continue;   // 일시정지 후 재개: 사이를 관측하지 못했으니 이동으로 세지 않는다
            }
            double dt = (b.timestamp() - a.timestamp()) / 1000.0;
            if (dt <= 0) {
                continue;
            }
            double d = haversineMeters(a, b);
            double kmh = d / dt * 3.6;
            if (kmh > TELEPORT_KMH) {
                teleport = true;
                continue;
            }
            if (dt >= GAP_SEC && d >= JUMP_MIN_M) {
                // GPS가 끊겼다가 점프: 지하철/터널 범위의 속도일 때만 인정
                if (kmh >= JUMP_MIN_KMH && kmh <= JUMP_MAX_KMH) {
                    jumpM += d;
                    jumps++;
                }
                continue;
            }
            if (d < JITTER_M || kmh < MOVING_KMH) {
                continue;   // 정지/흔들림
            }
            regularM += d;
            movingSec += dt;
            if (kmh >= VEHICLE_KMH) {
                vehicleSec += dt;
            }
            maxKmh = Math.max(maxKmh, kmh);
            movingSegments.add(new double[]{kmh, dt});
        }
        return new Stats(points.size(), regularM / 1000.0, jumpM / 1000.0, jumps,
                movingSec, vehicleSec, weightedP95(movingSegments, movingSec), maxKmh, teleport);
    }

    private static double weightedP95(List<double[]> segments, double totalSec) {
        if (segments.isEmpty() || totalSec <= 0) {
            return 0;
        }
        segments.sort(Comparator.comparingDouble(s -> s[0]));
        double acc = 0;
        for (double[] s : segments) {
            acc += s[1];
            if (acc >= totalSec * 0.95) {
                return s[0];
            }
        }
        return segments.get(segments.size() - 1)[0];
    }

    // ------------------------------------------------------------------ 판정

    /**
     * 이동수단 주장(mode)이 경로와 맞는지 판정한다.
     *
     * @param mode        WALK | TRANSIT | CAR
     * @param route       시각이 있는 경로 (시각이 없는 점은 버려진다)
     * @param nowMillis   지금 시각 (경로가 너무 오래됐는지 확인용)
     */
    public static Verdict evaluate(String mode, List<Point> route, long nowMillis) {
        if ("CAR".equals(mode)) {
            // 자차는 절감량이 0이라 속도 검증이 필요 없다 (거리는 앱 값을 쓰지 않고 경로로 계산)
            Stats stats = analyze(route == null ? List.of() : route);
            return Verdict.ok("자차 이동으로 기록했어요.", "UNKNOWN", stats.regularKm() + stats.jumpKm());
        }
        if (route == null || route.size() < MIN_POINTS) {
            return Verdict.rejected("이동 경로 기록이 부족해서 확인하지 못했어요. 위치 권한과 GPS를 켜고 다시 이동해 보세요.", "UNKNOWN");
        }
        long lastTs = route.get(route.size() - 1).timestamp();
        if (lastTs < nowMillis - MAX_AGE_MS) {
            return Verdict.rejected("이동이 끝난 지 오래돼서 인정할 수 없어요. 이동을 마치면 바로 인증해 주세요.", "UNKNOWN");
        }
        if (lastTs > nowMillis + FUTURE_TOLERANCE_MS) {
            return Verdict.rejected("기기 시간이 맞지 않아 이동을 확인하지 못했어요.", "UNKNOWN");
        }

        Stats stats = analyze(route);
        if (stats.usablePoints() < MIN_POINTS) {
            return Verdict.rejected("이동 경로 기록이 부족해서 확인하지 못했어요.", "UNKNOWN");
        }
        if (stats.teleport()) {
            return Verdict.rejected("위치 기록에 비정상적인 점프가 있어 인정할 수 없어요.", "UNKNOWN");
        }

        if ("WALK".equals(mode)) {
            if (stats.p95Kmh() > WALK_MAX_P95_KMH || stats.vehicleShare() >= WALK_MAX_VEHICLE_SHARE) {
                return Verdict.rejected("걷기보다 빠른 이동이 많이 보여요. 이동수단을 다시 확인해 주세요.", "VEHICLE");
            }
            // 도보는 GPS 끊김 점프를 거리로 세지 않는다 (걸어서 점프할 일이 없다)
            return Verdict.ok("도보 이동이 확인됐어요.", "WALK", stats.regularKm());
        }

        // TRANSIT
        double total = stats.regularKm() + stats.jumpKm();
        if (total < MIN_DISTANCE_KM) {
            return Verdict.rejected("이동 거리가 너무 짧아요. 0.3km 이상 이동해야 인정돼요.", "UNKNOWN");
        }
        if (stats.jumpKm() >= SUBWAY_JUMP_MIN_KM) {
            return Verdict.ok("지하철 이동 패턴(GPS가 끊겼다가 이어진 구간)이 확인됐어요.", "SUBWAY", total);
        }
        if (stats.p95Kmh() > TRANSIT_MAX_P95_KMH) {
            return Verdict.rejected("시속 " + (int) TRANSIT_MAX_P95_KMH + "km를 넘는 구간이 있어요. 시내 대중교통 이동으로 보기 어려워요.", "VEHICLE");
        }
        if (stats.vehicleSec() >= VEHICLE_MIN_SEC && stats.vehicleShare() >= VEHICLE_SHARE_MIN) {
            return Verdict.ok("버스 등 차량 이동 속도가 확인됐어요.", "VEHICLE", total);
        }
        return Verdict.rejected("걷는 속도 위주로 이동했어요. 버스나 지하철을 탄 이동만 대중교통으로 인정돼요.", "WALK");
    }

    // ------------------------------------------------------------------ 거리

    static double haversineMeters(Point a, Point b) {
        double r = 6_371_000;
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
