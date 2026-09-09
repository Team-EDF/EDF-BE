package com.edf.teamedf.domain.activity.command.application.service;

import com.edf.teamedf.domain.activity.command.application.dto.CertifyRequest;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivityResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivitySummaryResponse;
import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.domain.EcoActivity;
import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.dashboard.command.domain.UserRanking;
import com.edf.teamedf.domain.dashboard.command.infrastructure.UserRankingRepository;
import com.edf.teamedf.domain.notification.command.application.service.NotificationService;
import com.edf.teamedf.domain.notification.command.domain.NotificationType;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 친환경 활동 인증 서비스.
 *
 * <p>인증 판정은 카테고리별 고정 규칙(EcoCategory)에 따른다.
 * 이미지 인식/AI 판정은 별도 AI 서비스의 책임이며 이 서비스에서 다루지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EcoActivityService {

    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final String RANKING_TYPE_CARBON = "탄소절감";

    private final EcoActivityRepository ecoActivityRepository;
    private final UserRepository userRepository;
    private final UserRankingRepository userRankingRepository;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 인증

    @Transactional
    public CertifyResponse certify(Long userId, CertifyRequest request) {
        if (request == null || request.category() == null || request.category().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "카테고리는 필수입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        EcoCategory category = EcoCategory.from(request.category());

        String comment = request.comment();
        if (comment != null && comment.length() > 500) {
            comment = comment.substring(0, 500);
        }
        String imageUrl = request.imageUri();
        if (imageUrl != null && imageUrl.length() > 1000) {
            imageUrl = imageUrl.substring(0, 1000);
        }

        EcoActivity activity = EcoActivity.builder()
                .user(user)
                .category(category)
                .imageUrl(imageUrl)
                .comment(comment)
                .savedCarbon(category.getSavedCarbon())
                .pointsEarned(category.getPoints())
                .detectionName(category.getDetectionName())
                .status(EcoActivity.Status.APPROVED)
                .build();

        ecoActivityRepository.save(activity);

        // 이번 달 탄소절감 랭킹에 누적 반영
        applyToRanking(user, category.getSavedCarbon(), category.getPoints());

        Float totalCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        Long totalPoints = ecoActivityRepository.sumPointsByUserId(userId);
        if (totalPoints == null) {
            totalPoints = 0L;
        }

        notificationService.notify(
                userId,
                NotificationType.CERTIFY,
                "인증이 완료되었어요",
                String.format("%s 인증으로 %.1fkg CO₂를 절감하고 %dP를 받았어요.",
                        category.getDisplayName(), category.getSavedCarbon(), category.getPoints()),
                "activity",
                activity.getActivityId(),
                null
        );

        notifyIfEvolved(userId, totalCarbon - category.getSavedCarbon(), totalCarbon);

        return CertifyResponse.of(activity, totalCarbon, totalPoints);
    }

    /** 이번 인증으로 캐릭터가 진화했다면 레벨업 알림을 보낸다. */
    private void notifyIfEvolved(Long userId, float carbonBefore, float carbonAfter) {
        CharacterLevel before = CharacterLevel.of(Math.max(carbonBefore, 0f));
        CharacterLevel after = CharacterLevel.of(carbonAfter);
        if (after.getLevel() <= before.getLevel()) {
            return;
        }

        notificationService.notify(
                userId,
                NotificationType.LEVEL_UP,
                String.format("Lv.%d 로 진화했어요!", after.getLevel()),
                String.format("'%s' 단계가 되었어요. %s", after.getTitle(), after.getTagline()),
                "profile",
                null,
                null
        );
    }

    private void applyToRanking(User user, float addedCarbon, int addedPoints) {
        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

        UserRanking ranking = userRankingRepository
                .findByUser_UserIdAndRankingTypeAndRankingPeriodAndPeriodStart(
                        user.getUserId(), RANKING_TYPE_CARBON, PERIOD_MONTHLY, periodStart)
                .orElseGet(() -> userRankingRepository.save(UserRanking.builder()
                        .user(user)
                        .rankingType(RANKING_TYPE_CARBON)
                        .rankingPeriod(PERIOD_MONTHLY)
                        .periodStart(periodStart)
                        .score(0f)
                        .rankPosition(0)
                        .totalCarbon(0f)
                        .totalSaving(0f)
                        .totalSpending(0)
                        .build()));

        ranking.accumulate(addedCarbon, addedPoints);

        long better = userRankingRepository.countBetterScores(
                RANKING_TYPE_CARBON, PERIOD_MONTHLY, periodStart, ranking.getScore());
        ranking.updateRankPosition((int) better + 1);

        userRankingRepository.save(ranking);
    }

    // ------------------------------------------------------------------ 조회

    public Page<EcoActivityResponse> getMyActivities(Long userId, String rawCategory, Pageable pageable) {
        if (rawCategory != null && !rawCategory.isBlank() && !"ALL".equalsIgnoreCase(rawCategory)) {
            EcoCategory category = EcoCategory.from(rawCategory);
            return ecoActivityRepository
                    .findAllByUser_UserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable)
                    .map(EcoActivityResponse::from);
        }
        return ecoActivityRepository
                .findAllByUser_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(EcoActivityResponse::from);
    }

    public EcoActivitySummaryResponse getMySummary(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalCount = ecoActivityRepository.countByUser_UserId(userId);
        long monthCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, monthStart, now);
        long weekCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, weekStart, now);
        long todayCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, todayStart, now);

        float totalSavedCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        Long points = ecoActivityRepository.sumPointsByUserId(userId);
        long totalPoints = points == null ? 0L : points;

        int level = CharacterLevel.levelOf(totalSavedCarbon);

        List<EcoActivitySummaryResponse.CategoryBreakdown> categories = buildBreakdown(userId, totalCount);
        int streakDays = calculateStreak(userId);

        return new EcoActivitySummaryResponse(
                totalCount, monthCount, weekCount, todayCount,
                totalSavedCarbon, totalPoints, level, streakDays, categories);
    }

    private List<EcoActivitySummaryResponse.CategoryBreakdown> buildBreakdown(Long userId, long totalCount) {
        List<Object[]> rows = ecoActivityRepository.aggregateByCategory(userId);
        List<EcoActivitySummaryResponse.CategoryBreakdown> result = new ArrayList<>();
        for (Object[] row : rows) {
            EcoCategory category = (EcoCategory) row[0];
            long count = ((Number) row[1]).longValue();
            float carbon = ((Number) row[2]).floatValue();
            int ratio = totalCount == 0 ? 0 : Math.round((count * 100f) / totalCount);
            result.add(new EcoActivitySummaryResponse.CategoryBreakdown(
                    category.name(), category.getDisplayName(), count, carbon, ratio));
        }
        return result;
    }

    /** 오늘(또는 어제)부터 거꾸로 이어지는 연속 인증 일수. */
    private int calculateStreak(Long userId) {
        LocalDateTime from = LocalDate.now().minusDays(59).atStartOfDay();
        List<EcoActivity> recent = ecoActivityRepository
                .findAllByUser_UserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, from);
        if (recent.isEmpty()) {
            return 0;
        }

        Set<LocalDate> days = new HashSet<>();
        for (EcoActivity a : recent) {
            if (a.getCreatedAt() != null) {
                days.add(a.getCreatedAt().toLocalDate());
            }
        }

        LocalDate cursor = LocalDate.now();
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1);
            if (!days.contains(cursor)) {
                return 0;
            }
        }

        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 최근 N일 일별 인증 건수/절감량 (홈 화면 미니 차트용). */
    public List<DailyPoint> getDailyTrend(Long userId, int days) {
        int span = Math.max(1, Math.min(days, 31));
        LocalDateTime from = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        List<EcoActivity> activities = ecoActivityRepository
                .findAllByUser_UserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, from);

        List<DailyPoint> result = new ArrayList<>();
        for (int i = span - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            long count = 0;
            float carbon = 0f;
            for (EcoActivity a : activities) {
                if (a.getCreatedAt() != null && a.getCreatedAt().toLocalDate().equals(date)) {
                    count++;
                    carbon += a.getSavedCarbon() == null ? 0f : a.getSavedCarbon();
                }
            }
            result.add(new DailyPoint(date, count, carbon));
        }
        return result;
    }

    public record DailyPoint(LocalDate date, long count, float savedCarbon) {
    }

    // ------------------------------------------------------------------ 삭제

    @Transactional
    public void delete(Long userId, Long activityId) {
        EcoActivity activity = ecoActivityRepository.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "인증 기록을 찾을 수 없습니다."));

        if (activity.getUser() == null || !activity.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 인증 기록만 삭제할 수 있습니다.");
        }
        ecoActivityRepository.delete(activity);
    }

    // ------------------------------------------------------------------ util

    private static float toFloat(Double value) {
        return value == null ? 0f : value.floatValue();
    }

    @SuppressWarnings("unused")
    private static LocalDateTime endOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }
}
