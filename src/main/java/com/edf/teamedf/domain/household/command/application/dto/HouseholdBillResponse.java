package com.edf.teamedf.domain.household.command.application.dto;

import java.util.List;

/**
 * 한 달 관리비 기록 (프론트 "관리비" 화면용). 목록 조회와 저장 결과에 같이 쓴다.
 *
 * @param source       BILL(입력한 항목이 모두 고지서로 인증됨) | PARTIAL(일부만 인증) | MANUAL(직접 입력)
 * @param verifiedKeys 고지서로 인증된 항목 (electricity, water, gas, heat)
 * @param rewardPoints 이 달 기록으로 지금까지 받은 절감 포인트 합계
 * @param pointsAwarded 이번 저장으로 새로 지급된 절감 포인트 (목록 조회에서는 0)
 * @param message      저장 결과 안내 문구 (목록 조회에서는 null)
 */
public record HouseholdBillResponse(
        Long id,
        String month,
        Double electricityKwh,
        Integer electricityKrw,
        Double waterM3,
        Integer waterKrw,
        Double gasM3,
        Integer gasKrw,
        Double heatGcal,
        Integer heatKrw,
        Float carbonKg,
        boolean estimated,
        List<Item> items,
        String source,
        List<String> verifiedKeys,
        List<Comparison> comparisons,
        int rewardPoints,
        int pointsAwarded,
        String message,
        long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp
) {

    /** 항목별 탄소 내역. basis: usage(사용량 기준) | spend(금액으로 추정) | none */
    public record Item(
            String key, String label, Double usage, String unit, Integer krw,
            Float carbonKg, String basis, boolean verified) {
    }

    /**
     * 같은 공과금의 과거 대비 비교.
     *
     * @param baselineBasis   LAST_YEAR(작년 같은 달) | RECENT_AVG(최근 몇 달 평균, 참고용) | NONE
     * @param rewardEligible  포인트 지급 대상 (지금 달과 작년 같은 달 모두 고지서 인증 + 5% 이상 감소)
     * @param awardedPoints   이 항목으로 받은 포인트
     */
    public record Comparison(
            String key, String label, Double baselineUsage, String baselineBasis, int baselineMonths,
            boolean baselineVerified, Double reductionPct, int tier, int tierPoints,
            boolean rewardEligible, int awardedPoints) {
    }
}
