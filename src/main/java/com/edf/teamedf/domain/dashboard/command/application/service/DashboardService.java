package com.edf.teamedf.domain.dashboard.command.application.service;

import com.edf.teamedf.domain.dashboard.command.application.dto.CarbonScoreResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.CategoryCarbonRatioResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.MonthlySpendingResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.WeeklyCarbonResponse;
import com.edf.teamedf.domain.dashboard.command.domain.IntegratedStat;
import com.edf.teamedf.domain.dashboard.command.infrastructure.CategoryStatRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.IntegratedStatRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.UserRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final String RANKING_TYPE_CARBON = "탄소절감";

    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final IntegratedStatRepository integratedStatRepository;
    private final CategoryStatRepository categoryStatRepository;
    private final UserRankingRepository userRankingRepository;

    // 탄소 절감 점수 조회 (이번달 기준)
    public CarbonScoreResponse getCarbonScore(Long userId) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        return userRankingRepository
                .findByUser_UserIdAndRankingTypeAndRankingPeriodAndPeriodStart(
                        userId, RANKING_TYPE_CARBON, PERIOD_MONTHLY, firstDayOfMonth)
                .map(CarbonScoreResponse::from)
                .orElse(CarbonScoreResponse.empty());
    }

    // 카테고리별 탄소 배출 비율 조회 (이번달 기준)
    public List<CategoryCarbonRatioResponse> getCategoryCarbonRatios(Long userId) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        return integratedStatRepository
                .findByUser_UserIdAndPeriodTypeAndPeriodStart(userId, PERIOD_MONTHLY, firstDayOfMonth)
                .map(stat -> categoryStatRepository.findByIntegratedStat_StatId(stat.getStatId())
                        .stream()
                        .map(CategoryCarbonRatioResponse::from)
                        .toList())
                .orElse(List.of());
    }

    // 이번달 소비 금액 조회
    public MonthlySpendingResponse getMonthlySpending(Long userId) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        return integratedStatRepository
                .findByUser_UserIdAndPeriodTypeAndPeriodStart(userId, PERIOD_MONTHLY, firstDayOfMonth)
                .map(MonthlySpendingResponse::from)
                .orElse(MonthlySpendingResponse.empty(firstDayOfMonth));
    }

    // 주간 탄소 배출량 그래프 조회 (오늘 포함 최근 7일)
    public List<WeeklyCarbonResponse> getWeeklyCarbonGraph(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        List<Object[]> rows = consumptionRecordRepository
                .findDailyCarbonByUserIdAndDateBetween(userId, weekAgo, today);

        // 쿼리 결과를 날짜 → 탄소량 맵으로 변환
        Map<LocalDate, Float> carbonByDate = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            Float carbon = ((Number) row[1]).floatValue();
            carbonByDate.put(date, carbon);
        }

        // 최근 7일 전체 날짜에 대해 값이 없으면 0으로 채움
        List<WeeklyCarbonResponse> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            result.add(WeeklyCarbonResponse.of(date, carbonByDate.getOrDefault(date, 0f)));
        }
        return result;
    }
}
