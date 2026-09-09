package com.edf.teamedf.domain.activity.command.application.dto;

import java.util.List;

/**
 * 내 활동 요약.
 *
 * <p>홈 화면의 "실천 목표 / 진행 중" 카드와 마이페이지 상단 통계에 사용한다.</p>
 */
public record EcoActivitySummaryResponse(
        long totalCount,
        long monthCount,
        long weekCount,
        long todayCount,
        float totalSavedCarbon,
        long totalPoints,
        int level,
        int streakDays,
        List<CategoryBreakdown> categories
) {

    /** 카테고리별 인증 횟수/절감량. */
    public record CategoryBreakdown(
            String category,
            String categoryName,
            long count,
            float savedCarbon,
            int ratio
    ) {
    }
}
