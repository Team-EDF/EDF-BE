package com.edf.teamedf.domain.dashboard.command.application.dto;

import com.edf.teamedf.domain.dashboard.command.domain.IntegratedStat;

import java.time.LocalDate;

public record MonthlySpendingResponse(
        int totalSpending,
        float totalCarbon,
        LocalDate periodStart
) {
    public static MonthlySpendingResponse from(IntegratedStat stat) {
        return new MonthlySpendingResponse(
                stat.getTotalSpending(),
                stat.getTotalCarbon(),
                stat.getPeriodStart()
        );
    }

    public static MonthlySpendingResponse empty(LocalDate periodStart) {
        return new MonthlySpendingResponse(0, 0f, periodStart);
    }
}
