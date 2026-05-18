package com.edf.teamedf.domain.dashboard.command.application.dto;

import java.time.LocalDate;

public record WeeklyCarbonResponse(
        LocalDate date,
        float carbonKg
) {
    public static WeeklyCarbonResponse of(LocalDate date, float carbonKg) {
        return new WeeklyCarbonResponse(date, carbonKg);
    }
}
