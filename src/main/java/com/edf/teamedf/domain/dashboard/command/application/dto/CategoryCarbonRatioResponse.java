package com.edf.teamedf.domain.dashboard.command.application.dto;

import com.edf.teamedf.domain.dashboard.command.domain.CategoryStat;

public record CategoryCarbonRatioResponse(
        Long categoryId,
        String categoryName,
        float categoryCarbon,
        int categorySpending,
        float percentage
) {
    public static CategoryCarbonRatioResponse from(CategoryStat stat) {
        return new CategoryCarbonRatioResponse(
                stat.getCategoryId(),
                stat.getCategoryName(),
                stat.getCategoryCarbon(),
                stat.getCategorySpending(),
                stat.getPercentage() != null ? stat.getPercentage() : 0f
        );
    }
}
