package com.edf.teamedf.domain.dashboard.command.application.dto;

import com.edf.teamedf.domain.dashboard.command.domain.UserRanking;

import java.time.LocalDate;

public record CarbonScoreResponse(
        float score,
        float totalSaving,
        int rankPosition,
        String rankingPeriod,
        LocalDate periodStart
) {
    public static CarbonScoreResponse from(UserRanking ranking) {
        return new CarbonScoreResponse(
                ranking.getScore(),
                ranking.getTotalSaving(),
                ranking.getRankPosition(),
                ranking.getRankingPeriod(),
                ranking.getPeriodStart()
        );
    }

    public static CarbonScoreResponse empty() {
        return new CarbonScoreResponse(0f, 0f, 0, "MONTHLY", LocalDate.now().withDayOfMonth(1));
    }
}
