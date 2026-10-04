package com.edf.teamedf.domain.challenge.command.application.service;

import com.edf.teamedf.domain.activity.command.application.service.EcoActivityService;
import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.challenge.command.application.dto.CheckInResponse;
import com.edf.teamedf.domain.challenge.command.application.dto.UserChallengeResponse;
import com.edf.teamedf.domain.challenge.command.application.dto.VerifyChallengeResponse;
import com.edf.teamedf.domain.challenge.command.domain.ChallengeCheckIn;
import com.edf.teamedf.domain.challenge.command.domain.ChallengeVerification;
import com.edf.teamedf.domain.challenge.command.domain.GreenProfile;
import com.edf.teamedf.domain.challenge.command.domain.UserChallenge;
import com.edf.teamedf.domain.challenge.command.infrastructure.ChallengeCheckInRepository;
import com.edf.teamedf.domain.challenge.command.infrastructure.ChallengeVerificationRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import com.edf.teamedf.domain.challenge.command.infrastructure.GreenProfileRepository;
import com.edf.teamedf.domain.challenge.command.infrastructure.UserChallengeRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Green Action 챌린지 서비스 (시제품).
 *
 * <ul>
 *   <li>설문 저장 + AI 프로필 계산</li>
 *   <li>이번 주 챌린지 3개 부여(AI 추천) / 조회</li>
 *   <li>자율 체크(하루 1회) / 대중교통 인증 연동 / 목표 달성 시 포인트 지급</li>
 * </ul>
 *
 * <p>무엇을 추천할지·포인트·난이도는 AI 서버와 카탈로그가 정하고, 여기서는 저장·진행도·지급만 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 이 거리(km)보다 짧은 이동은 대중교통 챌린지로 세지 않는다 (0km 인증으로 포인트를 얻는 것 방지). */
    private static final float MIN_TRANSIT_KM = 0.3f;
    /** 설문에서 AI로 넘기는 필드 (그 외 필드는 버린다). */
    private static final Set<String> SURVEY_KEYS = Set.of(
            "transport", "transport_spend", "cafe_drink", "food", "shopping", "eco_interest", "goal_intent");

    /** 사진 인증 한 번마다 주는 보너스 포인트 (챌린지 완료 포인트와 별도). */
    private static final int VERIFY_BONUS_POINTS = 5;
    /** 하루에 보너스를 받을 수 있는 인증 횟수. 인증 자체는 무제한이고, 보너스 포인트만 하루 한도가 있다(포인트 파밍 방지). */
    private static final int VERIFY_BONUS_DAILY_CAP = 3;

    /** 최근 이 기간(일)의 챌린지 완료 수로 GSTI 태도 축을 갱신한다. */
    private static final int RECENT_COMPLETION_DAYS = 30;
    /** GSTI가 바뀐 뒤 "바뀌었어요" 안내를 계속 보여 주는 기간(일). */
    private static final int TYPE_CHANGE_NOTICE_DAYS = 7;

    /**
     * 설문은 처음 한 번만 할 수 있다 (다시 하면 형평성이 어긋남). 이후 GSTI는 소비 데이터로 자동 갱신된다.
     * 로컬 테스트에서만 GREEN_SURVEY_ALLOW_RETAKE=true 로 다시 할 수 있게 한다.
     */
    @Value("${green.survey.allow-retake:false}")
    private boolean allowSurveyRetake;

    private final GreenProfileRepository greenProfileRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final ChallengeCheckInRepository checkInRepository;
    private final ChallengeVerificationRepository verificationRepository;
    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final UserRepository userRepository;
    private final EcoActivityRepository ecoActivityRepository;
    private final EcoActivityService ecoActivityService;
    private final GreenAiClient greenAiClient;
    // 이 프로젝트의 다른 서비스(RecordConfirmService 등)처럼 직접 만든다 (Jackson 2 ObjectMapper 빈이 없음)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ 설문 / 프로필

    /** 설문 답변을 저장하고 AI로 Green Profile(GSTI)을 계산해 보관한다. 설문은 처음 한 번만 가능하다. */
    @Transactional
    public Map<String, Object> submitSurvey(Long userId, Map<String, Object> rawAnswers) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (!allowSurveyRetake && greenProfileRepository.findByUser_UserId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "설문은 처음 한 번만 할 수 있어요. 이후 GSTI는 영수증·소비 데이터로 자동 업데이트돼요.");
        }

        Map<String, Object> answers = new LinkedHashMap<>();
        for (String key : SURVEY_KEYS) {
            Object value = rawAnswers == null ? null : rawAnswers.get(key);
            if (value instanceof String text && !text.isBlank()) {
                answers.put(key, text.trim());
            }
        }
        if (answers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "설문 답변이 비어 있습니다.");
        }

        Map<String, Object> aiRequest = new LinkedHashMap<>(answers);
        aiRequest.put("user_id", userId);
        aiRequest.put("recent_challenge_completions", recentCompletions(userId));
        Map<String, Object> profile = greenAiClient.createProfile(aiRequest);

        String answersJson = toJson(answers);
        String profileJson = toJson(profile);
        String source = profile.get("source") instanceof String s ? s : "survey";
        String typeCode = extractTypeCode(profile);

        GreenProfile entity = greenProfileRepository.findByUser_UserId(userId)
                .orElseGet(() -> GreenProfile.builder().user(user).build());
        entity.update(answersJson, profileJson, source, typeCode);
        greenProfileRepository.save(entity);
        return profile;
    }

    /** 내 프로필. 하루에 한 번, 설문 이후 쌓인 영수증·챌린지 기록으로 GSTI를 자동으로 다시 계산한다. */
    @Transactional
    public Map<String, Object> getMyProfile(Long userId) {
        GreenProfile profile = greenProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "설문을 먼저 진행해 주세요."));
        refreshProfileIfStale(profile);
        return fromJson(profile.getProfileJson());
    }

    /**
     * 저장된 설문 답변에 최신 영수증·챌린지 완료 기록을 더해 AI로 프로필을 다시 계산한다 (하루 1회).
     * 실패해도(AI 서버 장애 등) 기존 프로필을 그대로 쓰도록 예외는 삼킨다.
     * 유형이 바뀌면 TYPE_CHANGE_NOTICE_DAYS 동안 "이전 유형" 정보를 프로필에 같이 담아 화면이 안내할 수 있게 한다.
     */
    private void refreshProfileIfStale(GreenProfile profile) {
        if (profile.getUpdatedAt() != null && !profile.getUpdatedAt().toLocalDate().isBefore(LocalDate.now())) {
            return;
        }
        try {
            Long userId = profile.getUser().getUserId();
            Map<String, Object> request = new LinkedHashMap<>(fromJson(profile.getSurveyAnswers()));
            request.put("user_id", userId);
            request.put("recent_challenge_completions", recentCompletions(userId));
            Map<String, Object> fresh = greenAiClient.createProfile(request);

            markTypeChange(fresh, fromJson(profile.getProfileJson()), profile.getTypeCode());
            String source = fresh.get("source") instanceof String s ? s : "survey";
            profile.update(profile.getSurveyAnswers(), toJson(fresh), source, extractTypeCode(fresh));
            greenProfileRepository.save(profile);
        } catch (Exception e) {
            log.warn("GSTI 자동 갱신 실패 (기존 프로필 유지): userId={}, {}", profile.getUser().getUserId(), e.toString());
        }
    }

    private long recentCompletions(Long userId) {
        return userChallengeRepository.countByUser_UserIdAndStatusAndCompletedAtAfter(
                userId, UserChallenge.STATUS_COMPLETED, LocalDateTime.now().minusDays(RECENT_COMPLETION_DAYS));
    }

    /** 새 프로필에 "유형이 바뀌었다"는 표시(type_changed_from / _name / _at)를 넣는다. 바뀌지 않았으면 최근 표시만 이어 간다. */
    @SuppressWarnings("unchecked")
    private void markTypeChange(Map<String, Object> fresh, Map<String, Object> old, String oldCode) {
        String newCode = extractTypeCode(fresh);
        if (oldCode != null && newCode != null && !oldCode.equals(newCode)) {
            Object persona = old.get("persona");
            Object oldName = persona instanceof Map<?, ?> map ? ((Map<String, Object>) map).get("type_name") : null;
            fresh.put("type_changed_from", oldCode);
            fresh.put("type_changed_from_name", oldName);
            fresh.put("type_changed_at", LocalDate.now().toString());
            return;
        }
        if (old.get("type_changed_at") instanceof String changedAt) {
            try {
                if (!LocalDate.parse(changedAt).isBefore(LocalDate.now().minusDays(TYPE_CHANGE_NOTICE_DAYS))) {
                    fresh.put("type_changed_from", old.get("type_changed_from"));
                    fresh.put("type_changed_from_name", old.get("type_changed_from_name"));
                    fresh.put("type_changed_at", changedAt);
                }
            } catch (Exception ignored) {
                // 날짜 형식이 이상하면 표시를 이어 가지 않는다
            }
        }
    }

    /** 챌린지 추천 문구에서 "OO님"으로 부를 이름. 이름이 없으면 닉네임, 둘 다 없으면 null. */
    private static String displayName(User user) {
        for (String candidate : new String[]{user.getName(), user.getNickname()}) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 챌린지 조회 / 부여

    /** 이번 주 챌린지 목록. 아직 없으면 AI 추천으로 3개를 부여한다. */
    @Transactional
    public List<UserChallengeResponse> getThisWeekChallenges(Long userId) {
        LocalDate weekStart = currentWeekStart();
        List<UserChallenge> challenges =
                userChallengeRepository.findByUser_UserIdAndWeekStartOrderBySlotAsc(userId, weekStart);
        if (challenges.isEmpty()) {
            challenges = assignChallenges(userId, weekStart);
        }
        // 무구매 챌린지: 체크한 날에 쇼핑 영수증이 뒤늦게 등록됐으면 그 체크를 취소한다
        for (UserChallenge c : challenges) {
            if (!c.isCompleted() && UserChallenge.CROSS_NO_SHOPPING_RECEIPT.equals(c.getCrossCheck())) {
                dropConflictingCheckIns(c);
            }
        }
        return toResponses(challenges);
    }

    private List<UserChallenge> assignChallenges(Long userId, LocalDate weekStart) {
        GreenProfile profile = greenProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "설문을 먼저 진행해 주세요."));
        // 새 주를 시작하는 시점에 GSTI도 최신 소비 데이터로 갱신해서 그 프로필로 추천한다
        refreshProfileIfStale(profile);
        User user = profile.getUser();

        // 지난주에 완료한 챌린지는 이번 주 추천에서 뺀다 (매주 같은 챌린지가 반복되지 않게)
        List<String> excludeIds = userChallengeRepository
                .findByUser_UserIdAndWeekStartOrderBySlotAsc(userId, weekStart.minusWeeks(1)).stream()
                .filter(UserChallenge::isCompleted)
                .map(UserChallenge::getChallengeId)
                .toList();

        Map<String, Object> aiRequest = new LinkedHashMap<>();
        aiRequest.put("profile", fromJson(profile.getProfileJson()));
        aiRequest.put("exclude_challenge_ids", excludeIds);
        String userName = displayName(user);
        if (userName != null) {
            aiRequest.put("user_name", userName);
        }
        Map<String, Object> recommended = greenAiClient.recommend(aiRequest);

        Object items = recommended.get("challenges");
        if (!(items instanceof List<?> list) || list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서버가 추천 챌린지를 돌려주지 않았습니다.");
        }

        List<UserChallenge> created = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) raw;
            created.add(UserChallenge.builder()
                    .user(user)
                    .weekStart(weekStart)
                    .slot(asInt(c.get("slot"), created.size() + 1))
                    .challengeId(asString(c.get("challenge_id"), 20))
                    .area(asString(c.get("area"), 20))
                    .areaLabel(asString(c.get("area_label"), 20))
                    .difficulty(asInt(c.get("difficulty"), 1))
                    .title(asString(c.get("title"), 100))
                    .description(asString(c.get("description"), 300))
                    .targetCount(asInt(c.get("target_count"), 1))
                    .unit(asString(c.get("unit"), 10))
                    .verification(asString(c.get("verification"), 20))
                    .points(asInt(c.get("points"), 0))
                    .estSavingKg(c.get("est_saving_kg") instanceof Number n ? n.floatValue() : null)
                    .reason(asString(c.get("reason"), 500))
                    .reasonSource(asString(c.get("reason_source"), 20))
                    .photoVerification(asString(c.get("photo_verification"), 20))
                    .selfCheckLimit(c.get("self_check_limit") instanceof Number limit ? limit.intValue() : null)
                    .crossCheck(asString(c.get("cross_check"), 30))
                    .build());
        }
        return userChallengeRepository.saveAll(created);
    }

    // ------------------------------------------------------------------ 진행 / 완료

    /** 자율 체크: 하루 1회만, 목표 횟수를 채우면 완료되고 포인트가 지급된다. */
    @Transactional
    public CheckInResponse checkIn(Long userId, Long userChallengeId) {
        UserChallenge challenge = userChallengeRepository
                .findByUserChallengeIdAndUser_UserId(userChallengeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."));

        if (challenge.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 완료한 챌린지예요.");
        }
        if (!UserChallenge.VERIFY_SELF.equals(challenge.getVerification())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "대중교통 챌린지는 이동을 인증하면 자동으로 반영돼요.");
        }

        LocalDate today = LocalDate.now(KST);
        if (checkInRepository.existsByUserChallenge_UserChallengeIdAndCheckDate(userChallengeId, today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘은 이미 체크했어요. 내일 다시 체크할 수 있어요.");
        }

        // 사진 인증이 있는 챌린지는 직접 체크를 일부만 인정한다. 나머지는 "인증하기"로 채운다.
        Integer selfLimit = challenge.getSelfCheckLimit();
        if (selfLimit != null) {
            long used = checkInRepository.countByUserChallenge_UserChallengeIdAndMethod(userChallengeId, "MANUAL");
            if (used >= selfLimit) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, selfLimit == 0
                        ? "이 챌린지는 '인증하기'로 완료할 수 있어요."
                        : "직접 체크는 이번 주 " + selfLimit + "회까지만 인정돼요. 나머지는 '인증하기'로 채워 주세요.");
            }
        }
        // 무구매 챌린지: 그날 쇼핑 영수증이 등록돼 있으면 인정하지 않는다
        if (UserChallenge.CROSS_NO_SHOPPING_RECEIPT.equals(challenge.getCrossCheck())) {
            dropConflictingCheckIns(challenge);
            if (consumptionRecordRepository.existsShoppingReceipt(userId, today)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "오늘 쇼핑 영수증이 등록돼 있어서 '무구매'로 체크할 수 없어요.");
            }
        }

        checkInRepository.save(ChallengeCheckIn.builder()
                .userChallenge(challenge).checkDate(today).method("MANUAL").build());

        EcoActivityService.ChallengeRewardResult reward = advance(userId, challenge);
        if (reward != null) {
            return new CheckInResponse(
                    toResponse(challenge), true, challenge.getPoints(), reward.totalPoints(),
                    reward.previousLevel(), reward.level(), reward.leveledUp());
        }
        long points = totalPoints(userId);
        int level = CharacterLevel.levelOf(totalSavedCarbon(userId), points);
        return new CheckInResponse(toResponse(challenge), false, 0, points, level, level, false);
    }

    /**
     * 사진 인증 (텀블러 + 영수증 / 저탄소 마크 + 영수증).
     * AI가 사진을 판정하고, 영수증 지문(날짜·시각·금액 해시)이 이미 쓰인 것이면 중복으로 거절한다.
     * 인증 횟수는 무제한이다. 인증하면 진행도가 오르고(완료 시 포인트), 인증 보너스를 하루 한도 안에서 준다.
     * 이미 완료한 챌린지도 인증할 수 있다 (이때는 보너스만).
     */
    @Transactional
    public VerifyChallengeResponse verify(Long userId, Long userChallengeId, List<MultipartFile> images) {
        UserChallenge challenge = userChallengeRepository
                .findByUserChallengeIdAndUser_UserId(userChallengeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."));
        String kind = challenge.getPhotoVerification();
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 챌린지는 사진 인증을 지원하지 않아요.");
        }
        if (images == null || images.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 1장 이상 올려 주세요.");
        }

        long pointsBefore = totalPoints(userId);
        int levelBefore = CharacterLevel.levelOf(totalSavedCarbon(userId), pointsBefore);

        Map<String, Object> ai = greenAiClient.verify(kind, images);
        String code = ai.get("code") instanceof String s ? s : "UNKNOWN";
        String message = ai.get("message") instanceof String s ? s : "인증 결과를 확인하지 못했어요.";
        Map<?, ?> receipt = ai.get("receipt") instanceof Map<?, ?> m ? m : Map.of();
        VerifyChallengeResponse.ReceiptSummary summary = new VerifyChallengeResponse.ReceiptSummary(
                receipt.get("merchant_name") instanceof String s ? s : null,
                parseDate(receipt.get("payment_date")),
                receipt.get("total_amount") instanceof Number n ? n.intValue() : null);

        if (!Boolean.TRUE.equals(ai.get("passed"))) {
            return VerifyChallengeResponse.rejected(code, message, summary, toResponse(challenge), pointsBefore, levelBefore);
        }
        String fingerprint = receipt.get("fingerprint") instanceof String s ? s : null;
        if (fingerprint == null || fingerprint.isBlank()) {
            return VerifyChallengeResponse.rejected("RECEIPT_UNREADABLE",
                    "영수증의 날짜·금액이 잘 보이게 다시 찍어 주세요.", summary, toResponse(challenge), pointsBefore, levelBefore);
        }
        if (verificationRepository.existsByFingerprint(fingerprint)) {
            return VerifyChallengeResponse.rejected("DUPLICATE_RECEIPT",
                    "이미 인증에 사용한 영수증이에요. 다른 영수증으로 인증해 주세요.", summary, toResponse(challenge), pointsBefore, levelBefore);
        }

        boolean bonusAllowed = verificationRepository
                .countByUser_UserIdAndBonusPointsGreaterThanAndCreatedAtGreaterThanEqual(
                        userId, 0, LocalDate.now().atStartOfDay()) < VERIFY_BONUS_DAILY_CAP;
        int bonus = bonusAllowed ? VERIFY_BONUS_POINTS : 0;
        verificationRepository.saveAndFlush(ChallengeVerification.builder()
                .userChallenge(challenge)
                .user(challenge.getUser())
                .kind(kind)
                .fingerprint(fingerprint)
                .merchantName(summary.merchantName() == null ? null
                        : summary.merchantName().substring(0, Math.min(summary.merchantName().length(), 255)))
                .paymentDate(summary.paymentDate())
                .totalAmount(summary.totalAmount())
                .evidence(ai.get("evidence") instanceof String s ? s : null)
                .bonusPoints(bonus)
                .build());

        boolean justCompleted = false;
        if (!challenge.isCompleted()) {
            justCompleted = advance(userId, challenge) != null;
        }
        if (bonus > 0) {
            ecoActivityService.recordChallengeBonus(userId, challenge.getTitle(), bonus);
        }

        long pointsAfter = totalPoints(userId);
        int levelAfter = CharacterLevel.levelOf(totalSavedCarbon(userId), pointsAfter);
        String note = bonus > 0 ? " 인증 보너스 +" + bonus + "P"
                : " (오늘 인증 보너스 한도에 도달해서 보너스 포인트는 없어요)";
        return new VerifyChallengeResponse(
                true, code, message + note, ai.get("evidence") instanceof String s ? s : null, summary,
                toResponse(challenge), justCompleted, justCompleted ? challenge.getPoints() : 0, bonus,
                pointsAfter, levelBefore, levelAfter, levelAfter > levelBefore);
    }

    private static LocalDate parseDate(Object value) {
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** 무구매 챌린지: 이미 한 직접 체크 중, 그날 쇼핑 영수증이 등록된 날은 취소하고 진행도를 되돌린다. */
    private void dropConflictingCheckIns(UserChallenge challenge) {
        Long userId = challenge.getUser().getUserId();
        List<ChallengeCheckIn> conflicts = checkInRepository
                .findByUserChallenge_UserChallengeIdAndMethod(challenge.getUserChallengeId(), "MANUAL").stream()
                .filter(ci -> consumptionRecordRepository.existsShoppingReceipt(userId, ci.getCheckDate()))
                .toList();
        if (conflicts.isEmpty()) {
            return;
        }
        checkInRepository.deleteAll(conflicts);
        challenge.resetProgress(challenge.getProgressCount() - conflicts.size());
        userChallengeRepository.save(challenge);
        log.info("무구매 체크 취소 (쇼핑 영수증과 충돌): userId={}, challenge={}, 취소 {}건",
                userId, challenge.getChallengeId(), conflicts.size());
    }

    /**
     * 대중교통 이동 인증이 들어왔을 때 이번 주 대중교통 챌린지(AUTO_TRANSIT)에 하루 1회 반영한다.
     * 챌린지 반영이 실패해도 이동 인증 자체는 성공해야 하므로 예외는 삼키고 로그만 남긴다.
     */
    @Transactional
    public void onTransitCertified(Long userId, String mode, Float distanceKm) {
        try {
            if (mode == null || !"TRANSIT".equalsIgnoreCase(mode.trim())) {
                return;
            }
            if (distanceKm == null || distanceKm < MIN_TRANSIT_KM) {
                return;
            }
            LocalDate today = LocalDate.now(KST);
            List<UserChallenge> targets = userChallengeRepository
                    .findByUser_UserIdAndWeekStartAndStatusAndVerification(
                            userId, currentWeekStart(), UserChallenge.STATUS_ACTIVE, UserChallenge.VERIFY_AUTO_TRANSIT);
            for (UserChallenge challenge : targets) {
                if (checkInRepository.existsByUserChallenge_UserChallengeIdAndCheckDate(
                        challenge.getUserChallengeId(), today)) {
                    continue;
                }
                checkInRepository.save(ChallengeCheckIn.builder()
                        .userChallenge(challenge).checkDate(today).method("AUTO_TRANSIT").build());
                advance(userId, challenge);
            }
        } catch (Exception e) {
            log.warn("대중교통 챌린지 반영 실패 (이동 인증은 정상 처리됨): userId={}, {}", userId, e.toString());
        }
    }

    /** 진행도를 올리고, 막 완료됐으면 포인트를 지급한다. 지급했으면 그 결과(누적/레벨 변화)를, 아니면 null 을 돌려준다. */
    private EcoActivityService.ChallengeRewardResult advance(Long userId, UserChallenge challenge) {
        boolean justCompleted = challenge.addProgress();
        userChallengeRepository.save(challenge);
        if (!justCompleted) {
            return null;
        }
        return ecoActivityService.recordChallengeReward(userId, challenge.getTitle(), challenge.getPoints());
    }

    // ------------------------------------------------------------------ 보조

    private List<UserChallengeResponse> toResponses(List<UserChallenge> challenges) {
        List<Long> ids = challenges.stream().map(UserChallenge::getUserChallengeId).toList();
        Set<Long> checkedToday = ids.isEmpty() ? Set.of()
                : checkInRepository.findByUserChallenge_UserChallengeIdInAndCheckDate(ids, LocalDate.now(KST)).stream()
                        .map(ci -> ci.getUserChallenge().getUserChallengeId())
                        .collect(Collectors.toSet());
        return challenges.stream()
                .map(c -> toResponse(c, checkedToday.contains(c.getUserChallengeId())))
                .toList();
    }

    private UserChallengeResponse toResponse(UserChallenge challenge) {
        boolean checkedToday = checkInRepository.existsByUserChallenge_UserChallengeIdAndCheckDate(
                challenge.getUserChallengeId(), LocalDate.now(KST));
        return toResponse(challenge, checkedToday);
    }

    private UserChallengeResponse toResponse(UserChallenge challenge, boolean checkedToday) {
        long selfChecks = checkInRepository.countByUserChallenge_UserChallengeIdAndMethod(
                challenge.getUserChallengeId(), "MANUAL");
        long verified = verificationRepository.countByUserChallenge_UserChallengeId(challenge.getUserChallengeId());
        return UserChallengeResponse.of(challenge, checkedToday, (int) selfChecks, (int) verified);
    }

    private long totalPoints(Long userId) {
        Long points = ecoActivityRepository.sumPointsByUserId(userId);
        return points == null ? 0L : points;
    }

    private float totalSavedCarbon(Long userId) {
        Double carbon = ecoActivityRepository.sumSavedCarbonByUserId(userId);
        return carbon == null ? 0f : carbon.floatValue();
    }

    /** 이번 주 월요일 (한국 시간 기준). */
    static LocalDate currentWeekStart() {
        return LocalDate.now(KST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @SuppressWarnings("unchecked")
    private static String extractTypeCode(Map<String, Object> profile) {
        Object persona = profile.get("persona");
        if (persona instanceof Map<?, ?> map && map.get("type_code") instanceof String code) {
            return code.length() > 10 ? code.substring(0, 10) : code;
        }
        return null;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String asString(Object value, int maxLength) {
        if (!(value instanceof String s)) {
            return null;
        }
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 직렬화 실패");
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 프로필을 읽지 못했습니다.");
        }
    }
}
