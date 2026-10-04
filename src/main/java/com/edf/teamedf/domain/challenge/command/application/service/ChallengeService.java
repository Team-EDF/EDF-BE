package com.edf.teamedf.domain.challenge.command.application.service;

import com.edf.teamedf.domain.activity.command.application.service.EcoActivityService;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.challenge.command.application.dto.CheckInResponse;
import com.edf.teamedf.domain.challenge.command.application.dto.UserChallengeResponse;
import com.edf.teamedf.domain.challenge.command.domain.ChallengeCheckIn;
import com.edf.teamedf.domain.challenge.command.domain.GreenProfile;
import com.edf.teamedf.domain.challenge.command.domain.UserChallenge;
import com.edf.teamedf.domain.challenge.command.infrastructure.ChallengeCheckInRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

    private final GreenProfileRepository greenProfileRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final ChallengeCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final EcoActivityRepository ecoActivityRepository;
    private final EcoActivityService ecoActivityService;
    private final GreenAiClient greenAiClient;
    // 이 프로젝트의 다른 서비스(RecordConfirmService 등)처럼 직접 만든다 (Jackson 2 ObjectMapper 빈이 없음)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ 설문 / 프로필

    /** 설문 답변을 저장하고 AI로 Green Profile을 계산해 보관한다. 다시 하면 덮어쓴다. */
    @Transactional
    public Map<String, Object> submitSurvey(Long userId, Map<String, Object> rawAnswers) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

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

    public Map<String, Object> getMyProfile(Long userId) {
        GreenProfile profile = greenProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "설문을 먼저 진행해 주세요."));
        return fromJson(profile.getProfileJson());
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
        return toResponses(challenges);
    }

    private List<UserChallenge> assignChallenges(Long userId, LocalDate weekStart) {
        GreenProfile profile = greenProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "설문을 먼저 진행해 주세요."));
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

        checkInRepository.save(ChallengeCheckIn.builder()
                .userChallenge(challenge).checkDate(today).method("MANUAL").build());

        int awarded = advance(userId, challenge);
        return new CheckInResponse(
                UserChallengeResponse.of(challenge, true), awarded > 0, awarded, totalPoints(userId));
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

    /** 진행도를 올리고, 막 완료됐으면 포인트를 지급한다. 지급한 포인트(없으면 0)를 돌려준다. */
    private int advance(Long userId, UserChallenge challenge) {
        boolean justCompleted = challenge.addProgress();
        userChallengeRepository.save(challenge);
        if (!justCompleted) {
            return 0;
        }
        ecoActivityService.recordChallengeReward(userId, challenge.getTitle(), challenge.getPoints());
        return challenge.getPoints();
    }

    // ------------------------------------------------------------------ 보조

    private List<UserChallengeResponse> toResponses(List<UserChallenge> challenges) {
        List<Long> ids = challenges.stream().map(UserChallenge::getUserChallengeId).toList();
        Set<Long> checkedToday = ids.isEmpty() ? Set.of()
                : checkInRepository.findByUserChallenge_UserChallengeIdInAndCheckDate(ids, LocalDate.now(KST)).stream()
                        .map(ci -> ci.getUserChallenge().getUserChallengeId())
                        .collect(Collectors.toSet());
        return challenges.stream()
                .map(c -> UserChallengeResponse.of(c, checkedToday.contains(c.getUserChallengeId())))
                .toList();
    }

    private long totalPoints(Long userId) {
        Long points = ecoActivityRepository.sumPointsByUserId(userId);
        return points == null ? 0L : points;
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
