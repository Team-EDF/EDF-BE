package com.edf.teamedf.domain.activity.command.domain;

import com.edf.teamedf.domain.activity.command.domain.TransitTripAnalyzer.Point;
import com.edf.teamedf.domain.activity.command.domain.TransitTripAnalyzer.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 속도 기반 이동수단 검증 테스트. 실제 이동 대신 "일정한 속도로 북쪽으로 가는" 합성 경로를 만들어 쓴다.
 * (서울 지하철 평균 표정속도 약 34km/h, 시내 버스 구간 속도, 도보 약 4~5km/h를 흉내 낸다)
 */
class TransitTripAnalyzerTest {

    private static final double METERS_PER_DEG_LAT = 111_320.0;
    private static final long T0 = 1_760_000_000_000L;   // 임의의 기준 시각 (epoch ms)

    /** 경로를 조립하는 도우미. 북쪽으로만 움직이고 시각은 ms 단위로 쌓는다. */
    private static final class Track {
        final List<Point> points = new ArrayList<>();
        double lat = 37.5665;
        long ts = T0;

        Track() {
            points.add(new Point(lat, 126.978, ts, false));
        }

        /** speedKmh 로 seconds 초 동안 stepSec 초마다 위치를 찍으며 이동. */
        Track move(double speedKmh, int seconds, int stepSec) {
            for (int t = stepSec; t <= seconds; t += stepSec) {
                lat += (speedKmh / 3.6 * stepSec) / METERS_PER_DEG_LAT;
                ts += stepSec * 1000L;
                points.add(new Point(lat, 126.978, ts, false));
            }
            return this;
        }

        /** 위치가 안 들어오는 시간(정차·터널·지하). 이동한 거리만큼 점프해서 다시 찍힌다. */
        Track gap(int seconds, double meters, boolean segmentBreak) {
            lat += meters / METERS_PER_DEG_LAT;
            ts += seconds * 1000L;
            points.add(new Point(lat, 126.978, ts, segmentBreak));
            return this;
        }

        long endTs() {
            return ts;
        }
    }

    private static Verdict evaluate(String mode, Track track) {
        return TransitTripAnalyzer.evaluate(mode, track.points, track.endTs() + 60_000);
    }

    // ------------------------------------------------------------------ 도보

    @Test
    void walkingIsAcceptedAsWalkButNotAsTransit() {
        Track walk = new Track().move(5, 600, 3);          // 5km/h로 10분 (약 0.83km)

        Verdict asWalk = evaluate("WALK", walk);
        assertThat(asWalk.ok()).isTrue();
        assertThat(asWalk.detectedMode()).isEqualTo("WALK");
        assertThat(asWalk.creditedKm()).isBetween(0.78, 0.88);

        Verdict asTransit = evaluate("TRANSIT", walk);
        assertThat(asTransit.ok()).isFalse();
        assertThat(asTransit.detectedMode()).isEqualTo("WALK");
        assertThat(asTransit.message()).contains("걷는 속도");
        assertThat(asTransit.creditedKm()).isZero();
    }

    @Test
    void joggingIsStillWalkingButCyclingIsNot() {
        assertThat(evaluate("WALK", new Track().move(10, 300, 3)).ok()).isTrue();      // 가벼운 달리기
        Verdict cycling = evaluate("WALK", new Track().move(22, 300, 3));              // 자전거 속도
        assertThat(cycling.ok()).isFalse();
        assertThat(cycling.message()).contains("걷기보다 빠른");
    }

    // ------------------------------------------------------------------ 버스

    @Test
    void busWithStopsIsAcceptedAsTransit() {
        Track bus = new Track();
        for (int stop = 0; stop < 4; stop++) {
            bus.move(32, 70, 3);          // 정류장 사이 주행
            bus.gap(30, 8, false);        // 정류장 정차: 위치 갱신 없이 30초, 제자리 근처
        }
        Verdict result = evaluate("TRANSIT", bus);
        assertThat(result.ok()).isTrue();
        assertThat(result.detectedMode()).isEqualTo("VEHICLE");
        assertThat(result.creditedKm()).isBetween(2.3, 2.6);     // 3초 간격 23번(69초) x 32km/h x 4구간 ≈ 2.45km
        // 같은 경로를 도보라고 하면 거절
        assertThat(evaluate("WALK", bus).ok()).isFalse();
    }

    @Test
    void walkToStopThenBusStillCountsAsTransit() {
        Track trip = new Track().move(5, 240, 3);       // 정류장까지 4분 걷기
        trip.gap(60, 10, false);                         // 버스 기다림
        trip.move(30, 300, 3);                           // 버스 5분
        Verdict result = evaluate("TRANSIT", trip);
        assertThat(result.ok()).isTrue();                // 이동 시간의 대부분이 차량 속도
    }

    @Test
    void carAtCityBusSpeedIsIndistinguishableFromBus() {
        // 속도만으로는 버스와 자가용을 구분할 수 없다는 한계를 문서화하는 테스트
        Verdict result = evaluate("TRANSIT", new Track().move(45, 300, 3));
        assertThat(result.ok()).isTrue();
    }

    @Test
    void highwaySpeedIsNotCityTransit() {
        Verdict result = evaluate("TRANSIT", new Track().move(140, 300, 3));
        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("130km");
    }

    // ------------------------------------------------------------------ 지하철 (GPS 끊김 후 점프)

    @Test
    void subwayGapJumpIsAcceptedAsTransit() {
        Track trip = new Track().move(5, 180, 3);       // 역까지 걷기 3분
        trip.gap(720, 8_000, false);                     // 지하 12분 동안 위치 없음, 8km 이동 (40km/h)
        trip.move(5, 120, 3);                            // 내려서 걷기 2분
        Verdict result = evaluate("TRANSIT", trip);
        assertThat(result.ok()).isTrue();
        assertThat(result.detectedMode()).isEqualTo("SUBWAY");
        assertThat(result.creditedKm()).isBetween(8.3, 8.6);   // 점프 8km + 걷기 약 0.4km
        // 같은 경로를 도보라고 하면 점프 거리는 인정하지 않는다 (걷기 부분만)
        Verdict asWalk = evaluate("WALK", trip);
        assertThat(asWalk.ok()).isTrue();
        assertThat(asWalk.creditedKm()).isLessThan(0.6);
    }

    @Test
    void jumpAfterPauseIsNotCreditedAsSubway() {
        // 앱에서 일시정지 -> (그 사이 자차로 8km 이동) -> 재개: 재개 지점이 segmentBreak 라 점프를 인정하지 않는다
        Track trip = new Track().move(5, 180, 3);
        trip.gap(720, 8_000, true);
        trip.move(5, 120, 3);
        Verdict result = evaluate("TRANSIT", trip);
        assertThat(result.ok()).isFalse();
        // 일시정지 없이 같은 점프가 있었다면(위의 지하철 경우) 인정되는 것과 대비
    }

    @Test
    void slowOrImplausibleJumpsAreNotCredited() {
        // 5분 동안 위치가 끊겼다가 400m 이동 (4.8km/h): 지하철이 아니라 신호 불량 중 걸은 것
        Track slow = new Track().move(5, 120, 3);
        slow.gap(300, 400, false);
        slow.move(5, 60, 3);
        assertThat(evaluate("TRANSIT", slow).ok()).isFalse();

        // 1분 만에 30km 이동 (1800km/h): 위치 오류
        Track teleport = new Track().move(30, 120, 3);
        teleport.gap(60, 30_000, false);
        teleport.move(30, 60, 3);
        Verdict result = evaluate("TRANSIT", teleport);
        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("비정상");
    }

    // ------------------------------------------------------------------ 기록 상태

    @Test
    void tooFewPointsOrTooShortOrStaleRoutesAreRejected() {
        assertThat(TransitTripAnalyzer.evaluate("TRANSIT", List.of(), T0).ok()).isFalse();
        assertThat(TransitTripAnalyzer.evaluate("WALK", null, T0).ok()).isFalse();

        Track tiny = new Track().move(30, 20, 3);        // 20초 약 0.17km
        Verdict tooShort = evaluate("TRANSIT", tiny);
        assertThat(tooShort.ok()).isFalse();
        assertThat(tooShort.message()).contains("0.3km");

        Track bus = new Track().move(30, 300, 3);
        long stale = bus.endTs() + TransitTripAnalyzer.MAX_AGE_MS + 1;
        assertThat(TransitTripAnalyzer.evaluate("TRANSIT", bus.points, stale).ok()).isFalse();
        long clockAhead = bus.endTs() - 10 * 60 * 1000L;
        assertThat(TransitTripAnalyzer.evaluate("TRANSIT", bus.points, clockAhead).ok()).isFalse();
    }

    @Test
    void duplicateOrOutOfOrderTimestampsAreIgnored() {
        Track bus = new Track().move(30, 300, 3);
        List<Point> noisy = new ArrayList<>(bus.points);
        noisy.add(5, noisy.get(5));                       // 같은 시각 중복
        noisy.add(10, noisy.get(2));                      // 과거 시각이 뒤늦게 들어옴
        Verdict clean = TransitTripAnalyzer.evaluate("TRANSIT", bus.points, bus.endTs());
        Verdict dirty = TransitTripAnalyzer.evaluate("TRANSIT", noisy, bus.endTs());
        assertThat(dirty.ok()).isTrue();
        assertThat(dirty.creditedKm()).isEqualTo(clean.creditedKm());
    }

    @Test
    void carModeIsRecordedWithoutSpeedChecks() {
        Verdict result = evaluate("CAR", new Track().move(50, 300, 3));
        assertThat(result.ok()).isTrue();
        assertThat(result.creditedKm()).isBetween(4.0, 4.5);
        assertThat(TransitTripAnalyzer.evaluate("CAR", null, T0).ok()).isTrue();     // 경로 없어도 자차는 절감량 0이라 기록 가능
    }

    @Test
    void speedStatisticsMatchSyntheticTrack() {
        TransitTripAnalyzer.Stats stats = TransitTripAnalyzer.analyze(new Track().move(36, 300, 3).points);
        assertThat(stats.p95Kmh()).isBetween(35.0, 37.0);
        assertThat(stats.vehicleShare()).isEqualTo(1.0);
        assertThat(stats.regularKm()).isBetween(2.9, 3.1);
        assertThat(stats.jumpCount()).isZero();
    }
}
