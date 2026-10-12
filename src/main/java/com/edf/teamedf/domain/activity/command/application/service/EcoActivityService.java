package com.edf.teamedf.domain.activity.command.application.service;

import com.edf.teamedf.domain.activity.command.application.dto.CertifyRequest;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivityResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivitySummaryResponse;
import com.edf.teamedf.domain.activity.command.application.dto.TransitCertifyRequest;
import com.edf.teamedf.domain.activity.command.application.dto.TransitCertifyResponse;
import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.domain.EcoActivity;
import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import com.edf.teamedf.domain.activity.command.domain.TransitTripAnalyzer;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.dashboard.command.domain.UserRanking;
import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;
import com.edf.teamedf.domain.image.command.infrastructure.UploadedImageRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.UserRankingRepository;
import com.edf.teamedf.domain.notification.command.application.service.NotificationService;
import com.edf.teamedf.domain.notification.command.domain.NotificationType;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
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
    private static final Set<String> VALID_TRANSIT_MODES = Set.of("WALK", "TRANSIT", "CAR");
    /** 이동 절감량 계산 (앱 transitModes.js 와 같은 값): 자차 기준 배출계수 대비 이동수단 배출계수 차이. */
    private static final float CAR_FACTOR = 0.192f;
    private static final float POINTS_PER_KG = 50f;

    private final EcoActivityRepository ecoActivityRepository;
    private final UserRepository userRepository;
    private final UserRankingRepository userRankingRepository;
    private final NotificationService notificationService;
    private final UploadedImageRepository uploadedImageRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 사진으로 직접 인증할 수 있는 카테고리. 대중교통은 GPS 이동 인증(/certify-transit)으로만 받는다. */
    private static final Set<EcoCategory> PHOTO_CATEGORIES = EnumSet.of(
            EcoCategory.TUMBLER, EcoCategory.RECYCLING, EcoCategory.ENERGY,
            EcoCategory.SHOPPING_BAG, EcoCategory.VEGETARIAN);

    /** 하루(KST) 같은 카테고리 사진 인증 횟수 상한. */
    @Value("${activity.certify.daily-limit-per-category:2}")
    private int dailyLimitPerCategory;

    /** 하루(KST) 사진 인증 전체 횟수 상한. */
    @Value("${activity.certify.daily-limit-total:6}")
    private int dailyLimitTotal;

    /** 인증 사진은 업로드한 지 이 시간 안에만 쓸 수 있다. */
    @Value("${activity.certify.image-max-age-hours:24}")
    private int imageMaxAgeHours;

    /** 하루(KST) GPS 이동 인증 횟수 상한. */
    @Value("${activity.transit.daily-limit:6}")
    private int transitDailyLimit;

    // ------------------------------------------------------------------ 챌린지 보상

    /**
     * Green Action 챌린지를 완료했을 때 포인트를 지급한다.
     * 기존 포인트 합계(sumPointsByUserId)와 월간 랭킹에 그대로 반영되도록 활동 기록 1건으로 남긴다.
     * 절감량(savedCarbon)은 챌린지 예상치가 측정값이 아니라서 0으로 둔다.
     * 대신 포인트가 쌓이므로 캐릭터 진화(탄소 + 포인트 하이브리드) 조건 중 포인트 쪽에 기여하고,
     * 이 보상으로 진화 조건을 모두 채우면 레벨업 알림을 보낸다.
     */
    @Transactional
    public ChallengeRewardResult recordChallengeReward(Long userId, String challengeTitle, int points) {
        return recordChallengeActivity(userId, "챌린지 완료 · " + challengeTitle, "챌린지 완료", points);
    }

    /** 사진 인증 보너스 포인트를 지급한다 (챌린지 완료 보상과 같은 방식으로 활동 기록 1건 + 랭킹 반영). */
    @Transactional
    public ChallengeRewardResult recordChallengeBonus(Long userId, String challengeTitle, int points) {
        return recordChallengeActivity(userId, "챌린지 인증 보너스 · " + challengeTitle, "챌린지 인증 보너스", points);
    }

    /** 관리비 절감 보상 포인트를 지급한다 (카테고리 HOUSEHOLD, 챌린지 보상과 같은 방식으로 기록·랭킹 반영·레벨업 판정). */
    @Transactional
    public ChallengeRewardResult recordHouseholdReward(Long userId, String label, int points) {
        return recordChallengeActivity(userId, EcoCategory.HOUSEHOLD, "관리비 절감 보상 · " + label, "관리비 절감 보상", points);
    }

    private ChallengeRewardResult recordChallengeActivity(Long userId, String commentText, String detectionName, int points) {
        return recordChallengeActivity(userId, EcoCategory.CHALLENGE, commentText, detectionName, points);
    }

    private ChallengeRewardResult recordChallengeActivity(
            Long userId, EcoCategory category, String commentText, String detectionName, int points) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String comment = commentText;
        if (comment.length() > 500) {
            comment = comment.substring(0, 500);
        }

        ecoActivityRepository.save(EcoActivity.builder()
                .user(user)
                .category(category)
                .comment(comment)
                .savedCarbon(0f)
                .pointsEarned(Math.max(0, points))
                .detectionName(detectionName)
                .status(EcoActivity.Status.APPROVED)
                .build());

        applyToRanking(user, 0f, Math.max(0, points));

        float totalCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        long totalPoints = toLong(ecoActivityRepository.sumPointsByUserId(userId));
        long pointsBefore = Math.max(totalPoints - Math.max(0, points), 0L);
        int previousLevel = CharacterLevel.levelOf(totalCarbon, pointsBefore);
        int currentLevel = CharacterLevel.levelOf(totalCarbon, totalPoints);
        notifyIfEvolved(userId, totalCarbon, pointsBefore, totalCarbon, totalPoints);
        return new ChallengeRewardResult(totalCarbon, totalPoints, previousLevel, currentLevel);
    }

    /** 챌린지 보상 지급 후의 누적 값과 레벨 변화. */
    public record ChallengeRewardResult(float totalSavedCarbon, long totalPoints, int previousLevel, int level) {
        public boolean leveledUp() {
            return level > previousLevel;
        }
    }

    // ------------------------------------------------------------------ 인증

    /**
     * 사진 인증. 남용을 막기 위해 다음을 확인한다.
     * <ul>
     *   <li>사진 필수: /images/upload(referenceType=ECO_ACTIVITY) 로 본인이 최근 올린, 아직 인증에 안 쓴 사진</li>
     *   <li>같은 내용의 사진(SHA-256)은 한 번만 인증에 쓸 수 있다</li>
     *   <li>하루(KST) 카테고리별 {@code dailyLimitPerCategory}회, 전체 {@code dailyLimitTotal}회</li>
     *   <li>대중교통은 사진이 아니라 GPS 이동 인증으로만 받는다</li>
     * </ul>
     * 같은 사용자의 동시 요청은 사용자 행 잠금으로 하나씩 처리해 횟수 검사를 우회하지 못하게 한다.
     */
    @Transactional
    public CertifyResponse certify(Long userId, CertifyRequest request) {
        if (request == null || request.category() == null || request.category().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "카테고리는 필수입니다.");
        }

        EcoCategory category = EcoCategory.from(request.category());
        if (category.isSystemOnly()) {
            // 챌린지·관리비 절감 보상 전용 카테고리라 직접 인증으로는 쓸 수 없다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 카테고리입니다: " + request.category());
        }
        if (category == EcoCategory.TRANSIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대중교통 이용은 이동 기록(GPS)으로 인증해 주세요.");
        }
        if (request.imageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "인증 사진이 필요해요. 앱을 최신 버전으로 업데이트한 뒤 다시 시도해 주세요.");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        LocalDateTime todayStart = startOfTodayKst();
        long categoryCount = ecoActivityRepository
                .countByUser_UserIdAndCategoryAndCreatedAtGreaterThanEqual(userId, category, todayStart);
        if (categoryCount >= dailyLimitPerCategory) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, String.format(
                    "'%s' 인증은 하루 %d번까지 할 수 있어요. 내일 다시 인증해 주세요.",
                    category.getDisplayName(), dailyLimitPerCategory));
        }
        long totalCount = ecoActivityRepository
                .countByUser_UserIdAndCategoryInAndCreatedAtGreaterThanEqual(userId, PHOTO_CATEGORIES, todayStart);
        if (totalCount >= dailyLimitTotal) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, String.format(
                    "사진 인증은 하루 %d번까지 할 수 있어요. 내일 다시 인증해 주세요.", dailyLimitTotal));
        }

        UploadedImage image = requireUsableImage(userId, request.imageId());

        String comment = request.comment();
        if (comment != null && comment.length() > 500) {
            comment = comment.substring(0, 500);
        }
        String imageUrl = image.getFileUrl();

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
        // 이 사진은 이제 이 인증에 쓰였으므로 다른 인증에 다시 쓸 수 없다.
        image.attachTo(ReferenceType.ECO_ACTIVITY, activity.getActivityId());

        // 이번 달 탄소절감 랭킹에 누적 반영
        applyToRanking(user, category.getSavedCarbon(), category.getPoints());

        Float totalCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        Long totalPoints = toLong(ecoActivityRepository.sumPointsByUserId(userId));

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

        float totalCarbonBefore = Math.max(totalCarbon - category.getSavedCarbon(), 0f);
        long totalPointsBefore = Math.max(totalPoints - category.getPoints(), 0L);
        CharacterLevel previousLevel = CharacterLevel.of(totalCarbonBefore, totalPointsBefore);
        CharacterLevel currentLevel = CharacterLevel.of(totalCarbon, totalPoints);
        notifyIfEvolved(userId, totalCarbonBefore, totalPointsBefore, totalCarbon, totalPoints);

        return CertifyResponse.of(
                activity,
                totalCarbon,
                totalPoints,
                previousLevel.getLevel(),
                currentLevel.getLevel()
        );
    }

    /**
     * GPS 기반 이동수단(도보/대중교통/자차) 탄소 절감 인증.
     *
     * <p>이동거리·절감량·포인트는 프론트가 GPS로 이미 계산해 보낸 값을 그대로 신뢰한다
     * (EcoCategory 처럼 카테고리별 고정값이 아니라 실제 이동거리에 비례하는 동적 값이기 때문).
     * 경로(route)는 재조회 용도가 없어 저장하지 않는다.</p>
     */
    @Transactional
    public TransitCertifyResponse certifyTransit(Long userId, TransitCertifyRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이동 수단(mode)은 필수입니다.");
        }
        String mode = request.mode().trim().toUpperCase();
        if (!VALID_TRANSIT_MODES.contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 이동 수단입니다: " + mode);
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String sessionId = request.sessionId();
        if (sessionId != null && !sessionId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바르지 않은 이동 세션 ID입니다.");
        }
        if (sessionId != null) {
            var previous = ecoActivityRepository.findByUser_UserIdAndTripSessionId(userId, sessionId);
            if (previous.isPresent()) {
                EcoActivity saved = previous.get();
                if (!mode.equals(saved.getTransitMode())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 저장한 이동의 수단을 바꿀 수 없습니다.");
                }
                float total = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
                long points = toLong(ecoActivityRepository.sumPointsByUserId(userId));
                int level = CharacterLevel.levelOf(total, points);
                return TransitCertifyResponse.of(saved.getSavedCarbon(), saved.getPointsEarned(), total, points,
                        level, level, "이미 저장된 이동 기록이에요.", saved.getTransitMode(), saved.getDistanceKm());
            }
        }

        // 앱이 보낸 거리·절감량·포인트는 믿지 않고, 시각이 있는 경로로 이동수단 주장을 검증한 뒤 서버가 다시 계산한다
        List<TransitTripAnalyzer.Point> routePoints = toAnalyzerPoints(request.route());
        TransitTripAnalyzer.Verdict verdict = TransitTripAnalyzer.evaluate(
                mode, routePoints, System.currentTimeMillis());
        if (verdict.ok()) {
            long todayTrips = ecoActivityRepository.countByUser_UserIdAndCategoryAndCreatedAtGreaterThanEqual(
                    userId, EcoCategory.TRANSIT, startOfTodayKst());
            if (todayTrips >= transitDailyLimit) {
                verdict = new TransitTripAnalyzer.Verdict(false, String.format(
                        "이동 인증은 하루 %d번까지 할 수 있어요. 내일 다시 기록해 주세요.", transitDailyLimit),
                        verdict.detectedMode(), 0);
            }
        }
        if (verdict.ok()) {
            // 같은 경로 재전송이나 시간이 겹치는 이동을 두 번 인증하지 못하게 한다.
            long tripStart = routePoints.stream().mapToLong(TransitTripAnalyzer.Point::timestamp).min().orElse(0L);
            long tripEnd = routePoints.stream().mapToLong(TransitTripAnalyzer.Point::timestamp).max().orElse(0L);
            if (ecoActivityRepository.existsOverlappingTrip(userId, EcoCategory.TRANSIT, tripStart, tripEnd)) {
                verdict = new TransitTripAnalyzer.Verdict(false,
                        "이미 인증한 이동과 시간이 겹쳐요. 같은 이동은 한 번만 인증할 수 있어요.",
                        verdict.detectedMode(), 0);
            }
        }
        if (!verdict.ok()) {
            float totalCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
            long totalPoints = toLong(ecoActivityRepository.sumPointsByUserId(userId));
            return TransitCertifyResponse.rejected(verdict.message(), verdict.detectedMode(),
                    totalCarbon, totalPoints, CharacterLevel.levelOf(totalCarbon, totalPoints));
        }
        float distanceKm = (float) verdict.creditedKm();
        float savedCarbon = Math.max(0f, distanceKm * (CAR_FACTOR - transitFactor(mode)));
        int pointsEarned = Math.round(savedCarbon * POINTS_PER_KG);
        String modeLabel = transitModeLabel(mode);

        String comment = String.format("GPS 이동 추적 · %.2fkm · %s",
                distanceKm, formatDuration(request.durationSec()));
        if (comment.length() > 500) {
            comment = comment.substring(0, 500);
        }

        EcoActivity activity = EcoActivity.builder()
                .user(user)
                .category(EcoCategory.TRANSIT)
                .imageUrl(null)
                .comment(comment)
                .savedCarbon(savedCarbon)
                .pointsEarned(pointsEarned)
                .detectionName("GPS 이동 추적 (" + modeLabel + ")")
                .tripSessionId(sessionId)
                .transitMode(mode)
                .distanceKm(distanceKm)
                .tripStartMs(routePoints.stream().mapToLong(TransitTripAnalyzer.Point::timestamp).min().orElse(0L))
                .tripEndMs(routePoints.stream().mapToLong(TransitTripAnalyzer.Point::timestamp).max().orElse(0L))
                .status(EcoActivity.Status.APPROVED)
                .build();

        ecoActivityRepository.save(activity);

        if (savedCarbon > 0f || pointsEarned > 0) {
            applyToRanking(user, savedCarbon, pointsEarned);
        }

        float totalCarbonAfter = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        float totalCarbonBefore = Math.max(totalCarbonAfter - savedCarbon, 0f);
        Long totalPoints = toLong(ecoActivityRepository.sumPointsByUserId(userId));
        long totalPointsBefore = Math.max(totalPoints - pointsEarned, 0L);
        CharacterLevel previousLevel = CharacterLevel.of(totalCarbonBefore, totalPointsBefore);
        CharacterLevel currentLevel = CharacterLevel.of(totalCarbonAfter, totalPoints);

        notificationService.notify(
                userId,
                NotificationType.CERTIFY,
                "이동 기록이 저장됐어요",
                String.format("%s 이동으로 %.1fkg CO₂를 절감하고 %dP를 받았어요.",
                        modeLabel, savedCarbon, pointsEarned),
                "activity",
                activity.getActivityId(),
                null
        );

        notifyIfEvolved(userId, totalCarbonBefore, totalPointsBefore, totalCarbonAfter, totalPoints);

        return TransitCertifyResponse.of(
                savedCarbon,
                pointsEarned,
                totalCarbonAfter,
                totalPoints,
                previousLevel.getLevel(),
                currentLevel.getLevel(),
                verdict.message(),
                verdict.detectedMode(),
                distanceKm
        );
    }

    /** 이동수단별 배출계수 (kg CO2/km): 도보 0, 대중교통 0.089, 자차 0.192. */
    private static float transitFactor(String mode) {
        return switch (mode) {
            case "WALK" -> 0f;
            case "CAR" -> CAR_FACTOR;
            default -> 0.089f;
        };
    }

    /** 요청 경로를 분석용 점으로 바꾼다. 시각·좌표가 없거나 정확도가 나쁜 점은 버린다. */
    private static List<TransitTripAnalyzer.Point> toAnalyzerPoints(List<TransitCertifyRequest.RoutePoint> route) {
        List<TransitTripAnalyzer.Point> points = new ArrayList<>();
        if (route == null) {
            return points;
        }
        for (TransitCertifyRequest.RoutePoint p : route) {
            if (p == null || p.latitude() == null || p.longitude() == null || p.timestamp() == null) {
                continue;
            }
            if (p.accuracy() != null && p.accuracy() > TransitTripAnalyzer.MAX_ACCURACY_M) {
                continue;
            }
            points.add(new TransitTripAnalyzer.Point(
                    p.latitude(), p.longitude(), p.timestamp(), Boolean.TRUE.equals(p.segmentBreak())));
        }
        return points;
    }

    private static String transitModeLabel(String mode) {
        return switch (mode) {
            case "WALK" -> "도보";
            case "CAR" -> "자차";
            default -> "대중교통";
        };
    }

    private static String formatDuration(Integer durationSec) {
        int sec = durationSec == null ? 0 : Math.max(0, durationSec);
        int minutes = sec / 60;
        int seconds = sec % 60;
        return String.format("%d분 %02d초", minutes, seconds);
    }

    /** 이번 인증으로 캐릭터가 진화했다면 레벨업 알림을 보낸다. */
    private void notifyIfEvolved(Long userId, float carbonBefore, long pointsBefore, float carbonAfter, long pointsAfter) {
        CharacterLevel before = CharacterLevel.of(Math.max(carbonBefore, 0f), Math.max(pointsBefore, 0L));
        CharacterLevel after = CharacterLevel.of(carbonAfter, pointsAfter);
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
        return getMyActivities(userId, rawCategory, pageable, null);
    }

    public Page<EcoActivityResponse> getMyActivities(Long userId, String rawCategory, Pageable pageable, String month) {
        java.time.YearMonth selected = com.edf.teamedf.common.util.CalendarMonth.parse(month);
        if (selected != null) {
            EcoCategory category = rawCategory == null || rawCategory.isBlank() || "ALL".equalsIgnoreCase(rawCategory)
                    ? null : EcoCategory.from(rawCategory);
            return ecoActivityRepository.findForMonth(userId, category,
                    selected.atDay(1).atStartOfDay(), selected.plusMonths(1).atDay(1).atStartOfDay(), pageable)
                    .map(EcoActivityResponse::from);
        }
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
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();

        long totalCount = ecoActivityRepository.countByUser_UserId(userId);
        long monthCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, monthStart, now);
        long weekCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, weekStart, now);
        long todayCount = ecoActivityRepository.countByUser_UserIdAndCreatedAtBetween(userId, todayStart, now);

        float totalSavedCarbon = toFloat(ecoActivityRepository.sumSavedCarbonByUserId(userId));
        long totalPoints = toLong(ecoActivityRepository.sumPointsByUserId(userId));

        int level = CharacterLevel.levelOf(totalSavedCarbon, totalPoints);

        List<EcoActivitySummaryResponse.CategoryBreakdown> categories = buildBreakdown(userId, totalCount);
        int streakDays = calculateStreak(userId);

        LocalDateTime end = today.plusDays(1).atStartOfDay();
        float monthSavedCarbon = toFloat(ecoActivityRepository.sumSavedCarbonInPeriod(userId, monthStart, end));
        Object[] monthly = transitStats(userId, monthStart, end);
        Object[] weekly = transitStats(userId, weekStart, end);
        return new EcoActivitySummaryResponse(
                totalCount, monthCount, weekCount, todayCount,
                totalSavedCarbon, totalPoints, level, streakDays,
                monthSavedCarbon, ((Number) monthly[0]).longValue(), ((Number) weekly[0]).longValue(),
                ((Number) monthly[1]).floatValue(), categories);
    }

    private Object[] transitStats(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = ecoActivityRepository.transitStats(userId, EcoCategory.TRANSIT,
                "GPS 이동 추적 (대중교통)", start, end);
        return rows.isEmpty() ? new Object[]{0L, 0d} : rows.get(0);
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
        // 삭제한 만큼 그 달 랭킹 점수도 되돌린다. (되돌리지 않으면 삭제 → 재인증으로 랭킹만 계속 올라간다)
        if (activity.getCreatedAt() != null) {
            LocalDate periodStart = activity.getCreatedAt().toLocalDate().withDayOfMonth(1);
            userRankingRepository
                    .findByUser_UserIdAndRankingTypeAndRankingPeriodAndPeriodStart(
                            userId, RANKING_TYPE_CARBON, PERIOD_MONTHLY, periodStart)
                    .ifPresent(ranking -> {
                        ranking.deduct(
                                activity.getSavedCarbon() == null ? 0f : activity.getSavedCarbon(),
                                activity.getPointsEarned() == null ? 0f : activity.getPointsEarned());
                        long better = userRankingRepository.countBetterScores(
                                RANKING_TYPE_CARBON, PERIOD_MONTHLY, periodStart, ranking.getScore());
                        ranking.updateRankPosition((int) better + 1);
                    });
        }
        ecoActivityRepository.delete(activity);
    }

    /** 활동 인증에 쓸 수 있는 사진인지 확인한다. */
    private UploadedImage requireUsableImage(Long userId, Long imageId) {
        UploadedImage image = uploadedImageRepository.findByImageIdAndUser_UserId(imageId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 사진을 찾을 수 없어요. 사진을 다시 올려 주세요."));
        if (image.getReferenceType() != ReferenceType.ECO_ACTIVITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "활동 인증용으로 올린 사진이 아니에요.");
        }
        if (image.getReferenceId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 인증에 사용한 사진이에요. 새 사진을 찍어 주세요.");
        }
        if (image.getCreatedAt() != null
                && image.getCreatedAt().isBefore(LocalDateTime.now().minusHours(imageMaxAgeHours))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오래전에 올린 사진은 쓸 수 없어요. 새 사진을 올려 주세요.");
        }
        if (image.getContentHash() != null
                && uploadedImageRepository.existsByContentHashAndReferenceTypeAndReferenceIdIsNotNull(
                        image.getContentHash(), ReferenceType.ECO_ACTIVITY)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 인증에 사용된 사진이에요. 새 사진을 찍어 주세요.");
        }
        return image;
    }

    /**
     * 오늘(KST) 0시를 서버 기본 시간대의 LocalDateTime 으로 돌려준다.
     * createdAt(@CreationTimestamp)은 서버 기본 시간대로 저장되므로, 서버가 UTC 여도 KST 하루 기준으로 센다.
     */
    private static LocalDateTime startOfTodayKst() {
        return LocalDate.now(KST).atStartOfDay(KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // ------------------------------------------------------------------ util

    private static float toFloat(Double value) {
        return value == null ? 0f : value.floatValue();
    }

    private static long toLong(Long value) {
        return value == null ? 0L : value;
    }

    @SuppressWarnings("unused")
    private static LocalDateTime endOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }
}
