package com.edf.teamedf.domain.household.command.domain;

import com.edf.teamedf.domain.household.command.domain.HouseholdSavings.Comparison;
import com.edf.teamedf.domain.household.command.domain.HouseholdSavings.MonthUsage;
import com.edf.teamedf.domain.household.command.domain.HouseholdSavings.Utility;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리비 절감 비교 규칙: 환경부 탄소중립포인트제처럼 작년 같은 달보다 5·10·15% 이상 줄이면 단계가 오르고,
 * 포인트는 "지금 달과 작년 같은 달이 모두 고지서로 인증된" 경우에만 지급한다.
 */
class HouseholdSavingsTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);

    private static MonthUsage usage(YearMonth month, Double value, boolean verified) {
        return new MonthUsage(month, value, verified);
    }

    @Test
    void tierBoundariesFollowThe5_10_15PercentSteps() {
        assertThat(HouseholdSavings.tierOf(0)).isZero();
        assertThat(HouseholdSavings.tierOf(4.99)).isZero();
        assertThat(HouseholdSavings.tierOf(5.0)).isEqualTo(1);
        assertThat(HouseholdSavings.tierOf(9.99)).isEqualTo(1);
        assertThat(HouseholdSavings.tierOf(10.0)).isEqualTo(2);
        assertThat(HouseholdSavings.tierOf(14.99)).isEqualTo(2);
        assertThat(HouseholdSavings.tierOf(15.0)).isEqualTo(3);
        assertThat(HouseholdSavings.tierOf(60)).isEqualTo(3);
        assertThat(HouseholdSavings.tierOf(-20)).isZero();        // 늘어난 경우
    }

    @Test
    void pointsPerTierAreProportionalToCarbonImpact() {
        assertThat(Utility.ELECTRICITY.pointsForTier(1)).isEqualTo(40);
        assertThat(Utility.ELECTRICITY.pointsForTier(3)).isEqualTo(120);
        assertThat(Utility.GAS.pointsForTier(2)).isEqualTo(60);
        assertThat(Utility.WATER.pointsForTier(3)).isEqualTo(30);      // 수도는 탄소 효과가 작아 포인트도 작다
        assertThat(Utility.ELECTRICITY.pointsForTier(0)).isZero();
        assertThat(Utility.ELECTRICITY.pointsForTier(9)).isEqualTo(120);   // 범위를 넘으면 마지막 단계
        assertThat(Utility.valueOfKey("gas")).contains(Utility.GAS);
        assertThat(Utility.valueOfKey("nope")).isEmpty();
    }

    @Test
    void lastYearSameMonthIsTheRewardBaseline() {
        // 작년 9월 300kWh -> 올해 9월 270kWh = 10% 감소 (2단계), 둘 다 인증이면 포인트 대상
        Comparison c = HouseholdSavings.compare(SEP, 270.0, true, List.of(usage(SEP.minusYears(1), 300.0, true)));
        assertThat(c.baselineBasis()).isEqualTo("LAST_YEAR");
        assertThat(c.baselineUsage()).isEqualTo(300.0);
        assertThat(c.reductionPct()).isEqualTo(10.0);
        assertThat(c.tier()).isEqualTo(2);
        assertThat(c.rewardEligible()).isTrue();
    }

    @Test
    void noRewardIfCurrentOrBaselineIsNotVerified() {
        Comparison currentManual = HouseholdSavings.compare(SEP, 270.0, false, List.of(usage(SEP.minusYears(1), 300.0, true)));
        assertThat(currentManual.tier()).isEqualTo(2);
        assertThat(currentManual.rewardEligible()).isFalse();                 // 절감은 보여 주지만 포인트는 없다

        Comparison baselineManual = HouseholdSavings.compare(SEP, 270.0, true, List.of(usage(SEP.minusYears(1), 300.0, false)));
        assertThat(baselineManual.rewardEligible()).isFalse();               // 직접 입력한 작년 값으로 부풀리는 것을 막는다
        assertThat(baselineManual.baselineVerified()).isFalse();
    }

    @Test
    void noRewardWhenUsageIncreasedOrBarelyChanged() {
        Comparison up = HouseholdSavings.compare(SEP, 330.0, true, List.of(usage(SEP.minusYears(1), 300.0, true)));
        assertThat(up.reductionPct()).isEqualTo(-10.0);
        assertThat(up.tier()).isZero();
        assertThat(up.rewardEligible()).isFalse();

        Comparison small = HouseholdSavings.compare(SEP, 290.0, true, List.of(usage(SEP.minusYears(1), 300.0, true)));
        assertThat(small.tier()).isZero();                                    // 3.3% 감소는 5% 미만
    }

    @Test
    void recentAverageIsReferenceOnlyBecauseOfSeasonality() {
        // 작년 데이터가 없고 직전 3개월이 있으면 평균과 비교하되 포인트는 주지 않는다 (냉난방 계절 효과)
        List<MonthUsage> others = List.of(
                usage(SEP.minusMonths(1), 400.0, true), usage(SEP.minusMonths(2), 380.0, true), usage(SEP.minusMonths(3), 420.0, true));
        Comparison c = HouseholdSavings.compare(SEP, 300.0, true, others);
        assertThat(c.baselineBasis()).isEqualTo("RECENT_AVG");
        assertThat(c.baselineMonths()).isEqualTo(3);
        assertThat(c.baselineUsage()).isEqualTo(400.0);
        assertThat(c.reductionPct()).isEqualTo(25.0);
        assertThat(c.tier()).isEqualTo(3);
        assertThat(c.rewardEligible()).isFalse();
    }

    @Test
    void lastYearWinsOverRecentAverage() {
        List<MonthUsage> others = List.of(
                usage(SEP.minusMonths(1), 400.0, true), usage(SEP.minusMonths(2), 380.0, true), usage(SEP.minusYears(1), 320.0, true));
        Comparison c = HouseholdSavings.compare(SEP, 300.0, true, others);
        assertThat(c.baselineBasis()).isEqualTo("LAST_YEAR");
        assertThat(c.baselineUsage()).isEqualTo(320.0);
    }

    @Test
    void recentAverageNeedsAtLeastTwoMonthsAndOnlyLooksBackThree() {
        assertThat(HouseholdSavings.compare(SEP, 300.0, true, List.of(usage(SEP.minusMonths(1), 400.0, true))).baselineBasis())
                .isEqualTo("NONE");
        // 4개월 전은 평균에 쓰지 않는다
        assertThat(HouseholdSavings.compare(SEP, 300.0, true,
                List.of(usage(SEP.minusMonths(1), 400.0, true), usage(SEP.minusMonths(4), 380.0, true))).baselineBasis())
                .isEqualTo("NONE");
    }

    @Test
    void acrossYearBoundaryAndMissingValues() {
        YearMonth jan = YearMonth.of(2026, 1);
        Comparison c = HouseholdSavings.compare(jan, 100.0, true,
                List.of(usage(YearMonth.of(2025, 12), 110.0, true), usage(YearMonth.of(2025, 11), 120.0, true)));
        assertThat(c.baselineBasis()).isEqualTo("RECENT_AVG");               // 12월, 11월은 1월의 직전 달들
        assertThat(c.baselineMonths()).isEqualTo(2);

        assertThat(HouseholdSavings.compare(SEP, null, true, List.of()).baselineBasis()).isEqualTo("NONE");
        assertThat(HouseholdSavings.compare(SEP, 0.0, true, List.of(usage(SEP.minusYears(1), 300.0, true))).tier()).isZero();
        // 작년 값이 비어 있거나 0이면 비교하지 않는다
        assertThat(HouseholdSavings.compare(SEP, 270.0, true, List.of(usage(SEP.minusYears(1), null, true))).baselineBasis())
                .isEqualTo("NONE");
        assertThat(HouseholdSavings.compare(SEP, 270.0, true, List.of(usage(SEP.minusYears(1), 0.0, true))).baselineBasis())
                .isEqualTo("NONE");
    }
}
