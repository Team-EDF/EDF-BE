package com.edf.teamedf.domain.household.command.application.service;

import com.edf.teamedf.domain.activity.command.application.service.EcoActivityService;
import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.challenge.command.application.service.GreenAiClient;
import com.edf.teamedf.domain.household.command.application.dto.BillReadResponse;
import com.edf.teamedf.domain.household.command.application.dto.HouseholdBillResponse;
import com.edf.teamedf.domain.household.command.application.dto.SaveBillRequest;
import com.edf.teamedf.domain.household.command.domain.HouseholdBill;
import com.edf.teamedf.domain.household.command.domain.HouseholdBillRead;
import com.edf.teamedf.domain.household.command.domain.HouseholdSavings;
import com.edf.teamedf.domain.household.command.domain.HouseholdSavings.Utility;
import com.edf.teamedf.domain.household.command.infrastructure.HouseholdBillReadRepository;
import com.edf.teamedf.domain.household.command.infrastructure.HouseholdBillRepository;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 가정 에너지(관리비) 서비스: 전기·수도·도시가스·지역난방의 월간 기록과 탄소, 작년 같은 달 대비 절감 포인트.
 *
 * <p>입력은 하이브리드다. 직접 입력할 수도 있고, 고지서 사진을 올리면 AI가 값을 읽어 미리 채워 준다
 * (사진은 저장하지 않는다). 읽은 값을 수정하지 않고 저장한 항목만 "고지서 인증"으로 기록하고,
 * 절감 포인트는 지금 달과 작년 같은 달이 모두 인증된 값일 때만 지급한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseholdService {

    private static final Logger log = LoggerFactory.getLogger(HouseholdService.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 고지서 읽기 결과를 저장에 쓸 수 있는 시간. */
    private static final long READ_TTL_HOURS = 2;
    /** 읽은 값과 입력값이 같은지 비교하는 허용 오차 (부동소수점 잡음만 허용; 지역난방은 소수 넷째 자리까지 저장). */
    private static final double TOLERANCE = 0.00005;

    private final HouseholdBillRepository billRepository;
    private final HouseholdBillReadRepository readRepository;
    private final UserRepository userRepository;
    private final EcoActivityRepository ecoActivityRepository;
    private final EcoActivityService ecoActivityService;
    private final GreenAiClient greenAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ 고지서 사진 읽기

    /** 고지서 사진(1~3장)을 AI로 읽는다. 읽은 값은 저장 전 "미리 채우기"용이고, 사진은 저장하지 않는다. */
    @Transactional
    public BillReadResponse read(Long userId, List<MultipartFile> images) {
        User user = getUser(userId);
        if (images == null || images.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "고지서 사진을 1장 이상 올려 주세요.");
        }
        readRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusHours(READ_TTL_HOURS));

        Map<String, Object> ai = greenAiClient.readHouseholdBill(images);
        boolean readable = Boolean.TRUE.equals(ai.get("readable"));
        String code = ai.get("code") instanceof String s ? s : "UNKNOWN";
        String message = ai.get("message") instanceof String s ? s : "고지서를 읽지 못했어요.";
        String month = ai.get("bill_month") instanceof String s ? s : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> values = ai.get("values") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        Integer totalKrw = ai.get("total_krw") instanceof Number n ? n.intValue() : null;
        String note = ai.get("note") instanceof String s ? s : null;

        String readId = null;
        if (readable && values != null && month != null) {
            readId = UUID.randomUUID().toString();
            readRepository.save(HouseholdBillRead.builder()
                    .readId(readId)
                    .user(user)
                    .billMonth(month)
                    .valuesJson(toJson(values))
                    .fingerprint(ai.get("fingerprint") instanceof String s ? s : null)
                    .build());
        }
        return new BillReadResponse(readId, readable, code, message, month, readable ? values : null, totalKrw, note);
    }

    // ------------------------------------------------------------------ 저장

    /** 한 달 관리비를 저장(덮어쓰기)하고 탄소를 계산한다. 조건이 맞으면 작년 같은 달 대비 절감 포인트를 지급한다. */
    @Transactional
    public HouseholdBillResponse save(Long userId, SaveBillRequest request) {
        User user = getUser(userId);
        YearMonth month = parseMonth(request.month());

        Double electricityKwh = checkedUsage(request.electricityKwh(), "전기 사용량", 3000);
        Integer electricityKrw = checkedKrw(request.electricityKrw(), "전기 금액");
        Double waterM3 = checkedUsage(request.waterM3(), "수도 사용량", 300);
        Integer waterKrw = checkedKrw(request.waterKrw(), "수도 금액");
        Double gasM3 = checkedUsage(request.gasM3(), "도시가스 사용량", 1000);
        Integer gasKrw = checkedKrw(request.gasKrw(), "도시가스 금액");
        Double heatGcal = checkedUsage(request.heatGcal(), "지역난방 사용량", 40, 10000.0);
        Integer heatKrw = checkedKrw(request.heatKrw(), "지역난방 금액");
        Double heatUsage = checkedUsage(request.heatUsage(), "지역난방 원본 사용량", 100000, 10000.0);
        String heatUnit = checkedHeatUnit(request.heatUnit());
        if ((heatUsage == null) != (heatUnit == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지역난방 원본 사용량과 단위를 함께 입력해 주세요.");
        }
        // 원본 난방 사용량과 Gcal 환산값의 일치 여부 검증
        if (heatUsage != null && heatUnit != null) {
            Double expectedGcal = switch (heatUnit) {
                case "Gcal" -> heatUsage;
                case "Mcal" -> heatUsage / 1000.0;
                case "kcal" -> heatUsage / 1_000_000.0;
                case "GJ" -> heatUsage / 4.1868;
                case "MJ" -> heatUsage / 4186.8;
                case "kWh" -> heatUsage * 3.6 / 4186.8;
                case "MWh" -> heatUsage * 3.6 / 4.1868;
                default -> null; // m3, ton은 열량으로 직접 환산 불가
            };

            if (expectedGcal == null) {
                if (heatGcal != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "난방 m3·ton 사용량은 Gcal로 직접 환산할 수 없어요.");
                }
            } else if (heatGcal != null
                    && Math.abs(heatGcal - expectedGcal) > 0.00005) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "지역난방 Gcal 환산값이 원본 사용량과 일치하지 않아요.");
            }
        }
        if (!hasPositive(electricityKwh, electricityKrw, waterM3, waterKrw, gasM3, gasKrw, heatGcal, heatKrw, heatUsage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전기·수도·가스·난방 중 하나 이상 값을 입력해 주세요.");
        }

        Optional<HouseholdBill> existing = billRepository.findByUser_UserIdAndBillMonth(userId, month.atDay(1));
        Map<Utility, double[]> values = new LinkedHashMap<>();   // {usage(없으면 NaN), krw(없으면 NaN)}
        values.put(Utility.ELECTRICITY, pair(electricityKwh, electricityKrw));
        values.put(Utility.WATER, pair(waterM3, waterKrw));
        values.put(Utility.GAS, pair(gasM3, gasKrw));
        values.put(Utility.HEAT, pair(heatGcal, heatKrw));

        // 고지서 인증: 읽은 값을 수정하지 않은 항목만. 읽기 없이 다시 저장해도 바뀌지 않은 항목은 인증이 유지된다
        List<String> verified = new ArrayList<>();
        String fingerprint = existing.map(HouseholdBill::getBillFingerprint).orElse(null);
        String extraNote = null;
        if (request.readId() != null && !request.readId().isBlank()) {
            HouseholdBillRead read = loadRead(userId, request.readId(), month);
            Map<String, Object> readValues = fromJson(read.getValuesJson());
            for (Utility utility : Utility.values()) {
                if (matchesRead(utility, values.get(utility), readValues)
                        && (utility != Utility.HEAT || matchesHeatRead(heatUsage, heatUnit, readValues))) {
                    verified.add(utility.key());
                }
            }
            if (!verified.isEmpty() && read.getFingerprint() != null) {
                if (billRepository.existsByBillFingerprintAndUser_UserIdNot(read.getFingerprint(), userId)) {
                    verified.clear();
                    extraNote = "이 고지서는 이미 다른 계정에서 인증에 사용돼서, 입력값으로만 저장했어요.";
                } else {
                    fingerprint = read.getFingerprint();
                }
            }
        }
        if (existing.isPresent()) {
            for (Utility utility : Utility.values()) {
                if (existing.get().isVerified(utility) && !verified.contains(utility.key())
                        && sameAsStored(existing.get(), utility, values.get(utility))
                        && (utility != Utility.HEAT || sameHeatStored(existing.get(), heatUsage, heatUnit))) {
                    verified.add(utility.key());
                }
            }
        }

        // 탄소 계산 (배출계수는 AI 서버가 관리한다)
        Map<String, Object> carbon = greenAiClient.householdCarbon(toAiValues(
                electricityKwh, electricityKrw, waterM3, waterKrw, gasM3, gasKrw, heatGcal, heatKrw, heatUsage, heatUnit));
        float carbonKg = carbon.get("total_kg") instanceof Number n ? n.floatValue() : 0f;
        boolean estimated = Boolean.TRUE.equals(carbon.get("estimated"));
        String carbonJson = toJson(carbon.get("items"));
        String verifiedKeys = String.join(",", verified);

        long pointsBefore = totalPoints(userId);
        int levelBefore = CharacterLevel.levelOf(totalSavedCarbon(userId), pointsBefore);

        HouseholdBill bill = existing.orElseGet(() -> HouseholdBill.builder().user(user).billMonth(month.atDay(1)).build());
        bill.apply(electricityKwh, electricityKrw, waterM3, waterKrw, gasM3, gasKrw, heatGcal, heatKrw,
                verifiedKeys, fingerprint, carbonKg, carbonJson, estimated, heatUsage, heatUnit);
        bill = billRepository.saveAndFlush(bill);

        // 절감 포인트: 이 달과, (이 달이 비교 기준이 될 수 있는) 1년 뒤 같은 달을 다시 평가한다
        List<HouseholdBill> all = billRepository.findByUser_UserIdOrderByBillMonthDesc(userId);
        int awarded = evaluateRewards(user, bill, all);
        Optional<HouseholdBill> nextYear = all.stream()
                .filter(b -> b.getBillMonth().equals(month.plusYears(1).atDay(1))).findFirst();
        if (nextYear.isPresent()) {
            awarded += evaluateRewards(user, nextYear.get(), all);
        }

        long pointsAfter = totalPoints(userId);
        int levelAfter = CharacterLevel.levelOf(totalSavedCarbon(userId), pointsAfter);
        String message = saveMessage(bill, all, awarded, extraNote);
        return toResponse(bill, all, awarded, message, pointsAfter, levelBefore, levelAfter);
    }

    // ------------------------------------------------------------------ 조회

    /** 내 관리비 기록 (최근 달부터). 비교·포인트 상태까지 계산해서 돌려준다. */
    public List<HouseholdBillResponse> list(Long userId) {
        List<HouseholdBill> all = billRepository.findByUser_UserIdOrderByBillMonthDesc(userId);
        long points = totalPoints(userId);
        int level = CharacterLevel.levelOf(totalSavedCarbon(userId), points);
        return all.stream().map(bill -> toResponse(bill, all, 0, null, points, level, level)).toList();
    }

    // ------------------------------------------------------------------ 절감 포인트

    /** 한 달 기록의 절감 포인트를 평가해서, 새로 올라간 단계만큼만 지급한다 (한 번 지급한 단계는 다시 주지 않는다). */
    private int evaluateRewards(User user, HouseholdBill bill, List<HouseholdBill> all) {
        Map<String, Map<String, Object>> state = fromRewardState(bill.getRewardState());
        int awardedNow = 0;
        YearMonth month = YearMonth.from(bill.getBillMonth());

        for (Utility utility : Utility.values()) {
            HouseholdSavings.Comparison comparison = compareFor(utility, bill, all);
            if (!comparison.rewardEligible()) {
                continue;
            }
            int oldTier = state.containsKey(utility.key()) && state.get(utility.key()).get("tier") instanceof Number n ? n.intValue() : 0;
            if (comparison.tier() <= oldTier) {
                continue;
            }
            int delta = utility.pointsForTier(comparison.tier()) - utility.pointsForTier(oldTier);
            ecoActivityService.recordHouseholdReward(user.getUserId(),
                    month + " " + utility.label() + " 작년 같은 달보다 " + Math.round(comparison.reductionPct()) + "% 절감",
                    delta);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tier", comparison.tier());
            entry.put("points", utility.pointsForTier(comparison.tier()));
            state.put(utility.key(), entry);
            awardedNow += delta;
        }
        if (awardedNow > 0) {
            int total = state.values().stream().mapToInt(e -> e.get("points") instanceof Number n ? n.intValue() : 0).sum();
            bill.updateReward(toJson(state), total);
            billRepository.save(bill);
        }
        return awardedNow;
    }

    private HouseholdSavings.Comparison compareFor(Utility utility, HouseholdBill bill, List<HouseholdBill> all) {
        // 기존 절감 포인트는 Gcal 기준으로만 비교한다. m3/MWh 원본값을
        // Gcal로 간주해 비교하면 잘못된 포인트가 지급될 수 있다.
        List<HouseholdSavings.MonthUsage> others = all.stream()
                .filter(b -> !Objects.equals(b.getBillId(), bill.getBillId()))
                .map(b -> new HouseholdSavings.MonthUsage(YearMonth.from(b.getBillMonth()), b.usageOf(utility), b.isVerified(utility)))
                .toList();
        return HouseholdSavings.compare(YearMonth.from(bill.getBillMonth()), bill.usageOf(utility), bill.isVerified(utility), others);
    }

    // ------------------------------------------------------------------ 응답 만들기

    private HouseholdBillResponse toResponse(HouseholdBill bill, List<HouseholdBill> all, int pointsAwarded, String message,
                                             long totalPoints, int levelBefore, int levelAfter) {
        List<HouseholdBillResponse.Item> items = new ArrayList<>();
        for (Map<String, Object> item : fromJsonList(bill.getCarbonJson())) {
            String key = item.get("key") instanceof String s ? s : "";
            boolean itemVerified = Utility.valueOfKey(key).map(bill::isVerified).orElse(false);
            items.add(new HouseholdBillResponse.Item(
                    key,
                    item.get("label") instanceof String s ? s : key,
                    item.get("usage") instanceof Number n ? n.doubleValue() : null,
                    item.get("unit") instanceof String s ? s : "",
                    item.get("krw") instanceof Number n ? n.intValue() : null,
                    item.get("carbon_kg") instanceof Number n ? n.floatValue() : 0f,
                    item.get("basis") instanceof String s ? s : "none",
                    itemVerified));
        }

        Map<String, Map<String, Object>> state = fromRewardState(bill.getRewardState());
        List<HouseholdBillResponse.Comparison> comparisons = new ArrayList<>();
        List<String> verifiedKeys = new ArrayList<>();
        int provided = 0;
        for (Utility utility : Utility.values()) {
            boolean has = bill.usageOf(utility) != null
                    || bill.krwOf(utility) != null
                    || (utility == Utility.HEAT && bill.getHeatUsage() != null);
            if (has) {
                provided++;
            }
            if (bill.isVerified(utility)) {
                verifiedKeys.add(utility.key());
            }
            HouseholdSavings.Comparison c = compareFor(utility, bill, all);
            if (!"NONE".equals(c.baselineBasis())) {
                int awardedPoints = state.containsKey(utility.key()) && state.get(utility.key()).get("points") instanceof Number n ? n.intValue() : 0;
                comparisons.add(new HouseholdBillResponse.Comparison(
                        utility.key(), utility.label(), c.baselineUsage(), c.baselineBasis(), c.baselineMonths(),
                        c.baselineVerified(), c.reductionPct(), c.tier(), utility.pointsForTier(c.tier()),
                        c.rewardEligible(), awardedPoints));
            }
        }
        long providedVerified = verifiedKeys.stream().filter(k -> Utility.valueOfKey(k)
                .map(u -> bill.usageOf(u) != null
                        || bill.krwOf(u) != null
                        || (u == Utility.HEAT && bill.getHeatUsage() != null))
                .orElse(false)).count();
        String source = providedVerified == 0 ? "MANUAL" : providedVerified >= provided ? "BILL" : "PARTIAL";

        return new HouseholdBillResponse(
                bill.getBillId(), YearMonth.from(bill.getBillMonth()).toString(),
                bill.getElectricityKwh(), bill.getElectricityKrw(), bill.getWaterM3(), bill.getWaterKrw(),
                bill.getGasM3(), bill.getGasKrw(), bill.getHeatGcal(),
                bill.getHeatUsage(), bill.getHeatUnit(), bill.getHeatKrw(),
                bill.getCarbonKg(), Boolean.TRUE.equals(bill.getEstimated()), items, source, verifiedKeys, comparisons,
                bill.getRewardPoints() == null ? 0 : bill.getRewardPoints(), pointsAwarded, message,
                totalPoints, levelBefore, levelAfter, levelAfter > levelBefore, bill.getCreatedAt(), bill.getUpdatedAt());
    }

    /** 저장 결과 안내: 지급된 포인트, 포인트를 못 받은 이유(인증·작년 같은 달 필요) 등을 알려 준다. */
    private String saveMessage(HouseholdBill bill, List<HouseholdBill> all, int awarded, String extraNote) {
        StringBuilder text = new StringBuilder("저장했어요. ");
        text.append(String.format("이번 달 집 에너지 탄소는 약 %.1fkg이에요.", bill.getCarbonKg() == null ? 0f : bill.getCarbonKg()));
        if (awarded > 0) {
            text.append(" 작년 같은 달보다 줄여서 절감 포인트 +").append(awarded).append("P를 받았어요!");
        } else {
            for (Utility utility : Utility.values()) {
                HouseholdSavings.Comparison c = compareFor(utility, bill, all);
                if ("LAST_YEAR".equals(c.baselineBasis()) && c.tier() >= 1 && !c.rewardEligible()) {
                    text.append(" ").append(utility.label()).append("이(가) 작년 같은 달보다 ")
                            .append(Math.round(c.reductionPct())).append("% 줄었어요. 이번 달과 작년 같은 달을 모두 고지서로 인증하면 포인트를 받을 수 있어요.");
                    break;
                }
            }
        }
        if (extraNote != null) {
            text.append(" ").append(extraNote);
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ 검증 / 도우미

    private YearMonth parseMonth(String text) {
        YearMonth month;
        try {
            month = YearMonth.parse(text == null ? "" : text.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용 월을 YYYY-MM 형식으로 입력해 주세요.");
        }

        YearMonth current = YearMonth.from(LocalDate.now(KST));

        // 과거 달은 기간 제한 없이 기록할 수 있고, 아직 오지 않은 달만 막는다.
        if (month.isAfter(current)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이번 달까지의 관리비만 기록할 수 있어요.");
        }

        return month;
    }

    private static Double checkedUsage(Double value, String name, double max) {
        return checkedUsage(value, name, max, 100.0);
    }

    /** scale: 100 = 소수 둘째 자리, 10000 = 소수 넷째 자리 (지역난방 Gcal: Mcal 고지서를 환산해도 자릿수가 유지되게) */
    private static Double checkedUsage(Double value, String name, double max, double scale) {
        if (value == null) {
            return null;
        }
        if (value.isNaN() || value < 0 || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 값이 올바르지 않아요.");
        }
        return Math.round(value * scale) / scale;
    }

    private static String checkedHeatUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }

        return switch (unit.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "m3", "m³", "㎥" -> "m3";
            case "ton", "t", "톤" -> "ton";
            case "gcal" -> "Gcal";
            case "mcal" -> "Mcal";
            case "kcal", "㎉" -> "kcal";
            case "gj" -> "GJ";
            case "mj" -> "MJ";
            case "kwh" -> "kWh";
            case "mwh" -> "MWh";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "지원하지 않는 지역난방 단위예요.");
        };
    }

    private static Integer checkedKrw(Integer value, String name) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 값이 올바르지 않아요.");
        }
        return value;
    }

    private static boolean hasPositive(Number... values) {
        for (Number value : values) {
            if (value != null && value.doubleValue() > 0) {
                return true;
            }
        }
        return false;
    }

    private static double[] pair(Double usage, Integer krw) {
        return new double[]{usage == null ? Double.NaN : usage, krw == null ? Double.NaN : krw};
    }

    private HouseholdBillRead loadRead(Long userId, String readId, YearMonth month) {
        HouseholdBillRead read = readRepository.findById(readId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "고지서 읽기 결과가 만료됐어요. 사진을 다시 올려 주세요."));
        if (!Objects.equals(read.getUser().getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "고지서 읽기 결과가 만료됐어요. 사진을 다시 올려 주세요.");
        }
        if (read.getCreatedAt() != null && read.getCreatedAt().isBefore(LocalDateTime.now().minusHours(READ_TTL_HOURS))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "고지서 읽기 결과가 만료됐어요. 사진을 다시 올려 주세요.");
        }
        // 월을 바꿨다면 읽은 값과 다른 고지서로 보고 인증하지 않는다 (예외 대신 "인증 없음"이 되도록 월 불일치를 표시)
        if (!read.getBillMonth().equals(month.toString())) {
            return HouseholdBillRead.builder().readId(read.getReadId()).user(read.getUser())
                    .billMonth(read.getBillMonth()).valuesJson("{}").fingerprint(null).build();
        }
        return read;
    }

    /** 입력한 (사용량, 금액)이 고지서에서 읽은 값과 같은 항목인지 (읽은 값이 하나라도 있어야 한다). */
    private boolean matchesRead(Utility utility, double[] input, Map<String, Object> readValues) {
        String prefix = utility.key();
        String usageKey = switch (utility) {
            case ELECTRICITY -> "electricity_kwh";
            case WATER -> "water_m3";
            case GAS -> "gas_m3";
            case HEAT -> "heat_gcal";
        };
        Double readUsage = readValues.get(usageKey) instanceof Number n ? n.doubleValue() : null;
        Double readKrw = readValues.get(prefix + "_krw") instanceof Number n ? n.doubleValue() : null;
        if (readUsage == null && readKrw == null) {
            // 난방은 Gcal/금액 없이 원본 사용량만 판독되는 고지서도 있다.
            return utility == Utility.HEAT && readValues.get("heat_usage") instanceof Number;
        }
        return equalValue(input[0], readUsage) && equalValue(input[1], readKrw);
    }

    private static boolean equalValue(double input, Double read) {
        if (Double.isNaN(input)) {
            return read == null;
        }
        return read != null && Math.abs(input - read) <= TOLERANCE;
    }

    private boolean matchesHeatRead(Double usage, String unit, Map<String, Object> readValues) {
        Double readUsage = readValues.get("heat_usage") instanceof Number n ? n.doubleValue() : null;
        String readUnit = readValues.get("heat_unit") instanceof String s ? checkedHeatUnit(s) : null;
        // 과거 판독 결과에는 원본 필드가 없을 수 있다. 이 경우 원본 입력도 없어야 한다.
        return equalValue(usage == null ? Double.NaN : usage, readUsage)
                && Objects.equals(unit, readUnit);
    }

    private static boolean sameHeatStored(HouseholdBill stored, Double usage, String unit) {
        return equalValue(usage == null ? Double.NaN : usage, stored.getHeatUsage())
                && Objects.equals(unit, stored.getHeatUnit());
    }

    private static boolean sameAsStored(HouseholdBill stored, Utility utility, double[] input) {
        Double usage = stored.usageOf(utility);
        Integer krw = stored.krwOf(utility);
        return equalValue(input[0], usage) && equalValue(input[1], krw == null ? null : krw.doubleValue());
    }

    private static Map<String, Object> toAiValues(Double electricityKwh, Integer electricityKrw, Double waterM3, Integer waterKrw,
                                                  Double gasM3, Integer gasKrw, Double heatGcal, Integer heatKrw,
                                                  Double heatUsage, String heatUnit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("electricity_kwh", electricityKwh);
        values.put("electricity_krw", electricityKrw);
        values.put("water_m3", waterM3);
        values.put("water_krw", waterKrw);
        values.put("gas_m3", gasM3);
        values.put("gas_krw", gasKrw);
        values.put("heat_gcal", heatGcal);
        values.put("heat_krw", heatKrw);
        values.put("heat_usage", heatUsage);
        values.put("heat_unit", heatUnit);
        return values;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private long totalPoints(Long userId) {
        Long points = ecoActivityRepository.sumPointsByUserId(userId);
        return points == null ? 0L : points;
    }

    private float totalSavedCarbon(Long userId) {
        Double carbon = ecoActivityRepository.sumSavedCarbonByUserId(userId);
        return carbon == null ? 0f : carbon.floatValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 직렬화 실패");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("관리비 JSON을 읽지 못했습니다: {}", e.toString());
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("관리비 탄소 내역 JSON을 읽지 못했습니다: {}", e.toString());
            return List.of();
        }
    }

    private Map<String, Map<String, Object>> fromRewardState(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
