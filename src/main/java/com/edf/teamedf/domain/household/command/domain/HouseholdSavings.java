package com.edf.teamedf.domain.household.command.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 관리비 절감 비교와 포인트 단계 계산 (순수 계산, 테스트하기 쉽게 DB와 분리).
 *
 * <p>환경부 탄소중립포인트제(에너지)의 기준을 차용한다: 과거 같은 달 대비 사용량을 5% 이상 줄이면 인센티브를
 * 주고, 5~10% / 10~15% / 15% 이상 3단계로 나뉜다. (전기 5천·1만·1.5만P, 도시가스 3천·6천·8천P,
 * 상수도 750·1,500·2,000P, 1P는 최대 2원) 우리 앱의 포인트 규모에 맞춰 같은 비율로 줄인 값을 쓴다.</p>
 *
 * <p><b>포인트 지급 조건 (악용 방지)</b>: 지금 달과 비교 기준 달이 모두 고지서로 인증된 값이어야 하고,
 * 비교 기준은 "작년 같은 달"이어야 한다. 최근 몇 달 평균은 계절(냉난방) 때문에 줄어든 것처럼 보일 수 있어서
 * 화면에 참고로만 보여 주고 포인트는 주지 않는다.</p>
 */
public final class HouseholdSavings {

    /** 공과금 종류와 단위, 단계별 포인트 (1단계=5%↑, 2단계=10%↑, 3단계=15%↑). */
    public enum Utility {
        ELECTRICITY("electricity", "전기", "kWh", new int[]{40, 80, 120}),
        WATER("water", "수도", "m3", new int[]{10, 20, 30}),
        GAS("gas", "도시가스", "m3", new int[]{30, 60, 90}),
        HEAT("heat", "지역난방", "Gcal", new int[]{30, 60, 90});

        private final String key;
        private final String label;
        private final String unit;
        private final int[] tierPoints;

        Utility(String key, String label, String unit, int[] tierPoints) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.tierPoints = tierPoints;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public String unit() {
            return unit;
        }

        public static Optional<Utility> valueOfKey(String key) {
            for (Utility utility : values()) {
                if (utility.key.equals(key)) {
                    return Optional.of(utility);
                }
            }
            return Optional.empty();
        }

        public int pointsForTier(int tier) {
            return tier <= 0 ? 0 : tierPoints[Math.min(tier, tierPoints.length) - 1];
        }
    }

    /** 비교 기준 후보가 되는 다른 달의 사용량. */
    public record MonthUsage(YearMonth month, Double usage, boolean verified) {
    }

    /**
     * 비교 결과.
     *
     * @param baselineUsage    비교 기준 사용량 (없으면 null)
     * @param baselineBasis    LAST_YEAR(작년 같은 달) | RECENT_AVG(최근 몇 달 평균, 참고용) | NONE
     * @param baselineMonths   최근 평균에 쓴 달 수 (LAST_YEAR는 1)
     * @param baselineVerified 기준이 모두 고지서로 인증된 값인지
     * @param reductionPct     기준 대비 감소율(%), 늘었으면 음수. 기준이 없으면 null
     * @param tier             0~3 (5%·10%·15% 이상)
     * @param rewardEligible   포인트 지급 대상인지 (지금 달 인증 + 작년 같은 달 인증 + 1단계 이상)
     */
    public record Comparison(
            Double baselineUsage, String baselineBasis, int baselineMonths, boolean baselineVerified,
            Double reductionPct, int tier, boolean rewardEligible) {

        static Comparison none() {
            return new Comparison(null, "NONE", 0, false, null, 0, false);
        }
    }

    private HouseholdSavings() {
    }

    public static int tierOf(double reductionPct) {
        if (reductionPct >= 15.0) {
            return 3;
        }
        if (reductionPct >= 10.0) {
            return 2;
        }
        if (reductionPct >= 5.0) {
            return 1;
        }
        return 0;
    }

    /**
     * 한 달·한 공과금의 비교를 계산한다.
     *
     * @param month           비교하려는 달
     * @param current         그 달 사용량 (없거나 0 이하면 비교하지 않음)
     * @param currentVerified 그 달 값이 고지서로 인증됐는지
     * @param others          같은 사용자의 다른 달 사용량들 (해당 공과금)
     */
    public static Comparison compare(YearMonth month, Double current, boolean currentVerified, List<MonthUsage> others) {
        if (current == null || current <= 0) {
            return Comparison.none();
        }

        // 1) 작년 같은 달 (계절을 맞춘 비교, 포인트 대상)
        for (MonthUsage other : others) {
            if (other.month().equals(month.minusYears(1)) && other.usage() != null && other.usage() > 0) {
                return build(current, other.usage(), "LAST_YEAR", 1, other.verified(), currentVerified);
            }
        }

        // 2) 직전 1~3개월 평균 (참고용: 계절 영향이 있어 포인트는 없다). 2개월 이상 있어야 한다
        List<MonthUsage> recent = new ArrayList<>();
        for (MonthUsage other : others) {
            long gap = (long) (month.getYear() - other.month().getYear()) * 12 + (month.getMonthValue() - other.month().getMonthValue());
            if (gap >= 1 && gap <= 3 && other.usage() != null && other.usage() > 0) {
                recent.add(other);
            }
        }
        if (recent.size() >= 2) {
            double average = recent.stream().mapToDouble(MonthUsage::usage).average().orElse(0);
            boolean allVerified = recent.stream().allMatch(MonthUsage::verified);
            return build(current, average, "RECENT_AVG", recent.size(), allVerified, currentVerified);
        }
        return Comparison.none();
    }

    private static Comparison build(double current, double baseline, String basis, int months,
                                    boolean baselineVerified, boolean currentVerified) {
        double reduction = (baseline - current) / baseline * 100.0;
        double rounded = Math.round(reduction * 10.0) / 10.0;
        int tier = tierOf(reduction);
        boolean eligible = "LAST_YEAR".equals(basis) && currentVerified && baselineVerified && tier >= 1;
        return new Comparison(Math.round(baseline * 100.0) / 100.0, basis, months, baselineVerified, rounded, tier, eligible);
    }
}
