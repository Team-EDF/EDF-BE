
package com.edf.teamedf.domain.chat.application;

import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionItem;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionItemRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import com.edf.teamedf.domain.household.command.application.dto.HouseholdBillResponse;
import com.edf.teamedf.domain.household.command.application.service.HouseholdService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int MAX_MESSAGE_LENGTH = 900;

    private static final String NO_RECEIPT_MESSAGE =
            "아직 확정된 영수증이 없어요. 영수증을 등록하고 확인하면 소비 기록을 바탕으로 알려드릴게요.";

    private static final String RECEIPT = "RECEIPT";
    private static final String HOUSEHOLD = "HOUSEHOLD";
    private static final String SUCCESS = "SUCCESS";

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 180_000;

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final ConsumptionItemRepository consumptionItemRepository;
    private final HouseholdService householdService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatResponse sendChatMessage(Long userId, ChatRequest request) {

        Long recordId = request.getRecordId();
        String chatType = request.getChatType();

        if (chatType == null || chatType.isBlank()) {
            chatType = RECEIPT;
        }

        if (!RECEIPT.equals(chatType) && !HOUSEHOLD.equals(chatType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 채팅 유형입니다."
            );
        }

        if (HOUSEHOLD.equals(chatType) && recordId != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "관리비 상담에서는 영수증 recordId를 사용할 수 없습니다."
            );
        }

        Map<String, Object> aiRequest = new HashMap<>();

        aiRequest.put("user_id", userId);
        aiRequest.put("chat_type", chatType);
        aiRequest.put("message", request.getMessage());

        if (request.getConversationId() != null) {
            aiRequest.put("conversation_id", request.getConversationId());
        }

        // 영수증 상담: Spring Boot DB의 실제 소비내역 전달
        if (RECEIPT.equals(chatType)) {

            Map<String, Object> receiptSummary;

            if (recordId != null) {
                ConsumptionRecord record =
                        consumptionRecordRepository.findById(recordId)
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "소비 기록을 찾을 수 없습니다."
                                ));

                if (!record.getUser().getUserId().equals(userId)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "본인의 소비 기록에 대해서만 피드백을 요청할 수 있습니다."
                    );
                }

                if (!SUCCESS.equalsIgnoreCase(record.getOcrStatus())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "확정된 영수증만 상담할 수 있습니다."
                    );
                }

                List<ConsumptionItem> items =
                        consumptionItemRepository.findByRecord_RecordIdIn(
                                List.of(recordId)
                        );

                receiptSummary = buildSingleReceiptSummary(record, items);
                aiRequest.put("record_id", recordId);

            } else {
                List<ConsumptionRecord> records =
                        consumptionRecordRepository
                                .findByUser_UserIdOrderByRecordDateDescRecordIdDesc(userId)
                                .stream()
                                .filter(r -> SUCCESS.equalsIgnoreCase(r.getOcrStatus()))
                                .toList();

                if (records.isEmpty()) {
                    return new ChatResponse(
                            null,
                            request.getConversationId(),
                            NO_RECEIPT_MESSAGE
                    );
                }

                List<Long> recordIds = records.stream()
                        .map(ConsumptionRecord::getRecordId)
                        .toList();

                List<ConsumptionItem> items =
                        consumptionItemRepository.findByRecord_RecordIdIn(recordIds);

                receiptSummary = buildAggregateSummary(userId, records, items);
            }

            aiRequest.put("receipt_summary", receiptSummary);
        }

        // 관리비 상담: 기존 기능 유지
        if (HOUSEHOLD.equals(chatType)) {
            List<HouseholdBillResponse> bills = householdService.list(userId);
            aiRequest.put("household_summary", bills);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(aiRequest, headers);

        Map<String, Object> aiResponse;

        try {
            aiResponse = restTemplate().postForObject(
                    aiServiceUrl + "/feedback/chat",
                    entity,
                    Map.class
            );

        } catch (HttpClientErrorException e) {

            log.warn(
                    "AI chat 4xx (userId={}, chatType={}, recordId={}, conversationId={}): {} {}",
                    userId,
                    chatType,
                    recordId,
                    request.getConversationId(),
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );

            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "소비 기록 또는 대화방을 찾을 수 없습니다."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI가 요청을 처리하지 못했습니다."
            );

        } catch (Exception e) {

            log.error(
                    "AI chat service error (userId={}, chatType={}, recordId={}, conversationId={})",
                    userId,
                    chatType,
                    recordId,
                    request.getConversationId(),
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버와의 통신에 실패했습니다."
            );
        }

        if (aiResponse == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI가 응답하지 않습니다."
            );
        }

        Long chatId = aiResponse.get("chat_id") != null
                ? Long.valueOf(aiResponse.get("chat_id").toString())
                : null;

        Long conversationId = aiResponse.get("conversation_id") != null
                ? Long.valueOf(aiResponse.get("conversation_id").toString())
                : request.getConversationId();

        String feedback = (String) aiResponse.get("feedback");

        return new ChatResponse(chatId, conversationId, feedback);
    }

    // 특정 영수증 요약
    private Map<String, Object> buildSingleReceiptSummary(
            ConsumptionRecord record,
            List<ConsumptionItem> items
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();

        List<Map<String, Object>> itemData =
                buildItemData(items, Map.of(record.getRecordId(), record));

        Map<String, Double> categoryCarbon = categoryCarbonSummary(items);
        Map<String, Integer> categoryAmount = categoryAmountSummary(items);

        summary.put("record_id", record.getRecordId());
        summary.put("merchant_name", record.getMerchantName());
        summary.put("payment_location", record.getPaymentLocation());
        summary.put("record_date", dateString(record.getRecordDate()));
        summary.put("total_amount_krw", safeAmount(record.getTotalAmount()));
        summary.put("total_carbon_kg", safeCarbon(record.getTotalCarbonKg()));
        summary.put("category_carbon_summary", categoryCarbon);
        summary.put("category_amount_summary", categoryAmount);
        summary.put("highest_carbon_category", highestCategory(categoryCarbon));
        summary.put("items", itemData);

        return summary;
    }

    // 여러 영수증 통합 요약
    private Map<String, Object> buildAggregateSummary(
            Long userId,
            List<ConsumptionRecord> records,
            List<ConsumptionItem> items
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();

        Map<Long, ConsumptionRecord> recordMap = records.stream()
                .collect(Collectors.toMap(
                        ConsumptionRecord::getRecordId,
                        r -> r
                ));

        List<Map<String, Object>> itemData = buildItemData(items, recordMap);

        int totalAmount = records.stream()
                .mapToInt(r -> safeAmount(r.getTotalAmount()))
                .sum();

        double totalCarbon = records.stream()
                .mapToDouble(r -> safeCarbon(r.getTotalCarbonKg()))
                .sum();

        Map<String, Double> categoryCarbon = categoryCarbonSummary(items);
        Map<String, Integer> categoryAmount = categoryAmountSummary(items);

        // 월별 소비 통계
        Map<String, Map<String, Object>> monthlySummary =
                new LinkedHashMap<>();

        for (ConsumptionRecord record : records) {
            if (record.getRecordDate() == null) {
                continue;
            }

            String month = YearMonth.from(record.getRecordDate()).toString();

            Map<String, Object> monthly = monthlySummary.computeIfAbsent(
                    month,
                    key -> {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("record_count", 0);
                        data.put("total_amount_krw", 0);
                        data.put("total_carbon_kg", 0.0);
                        return data;
                    }
            );

            monthly.put(
                    "record_count",
                    ((Number) monthly.get("record_count")).intValue() + 1
            );

            monthly.put(
                    "total_amount_krw",
                    ((Number) monthly.get("total_amount_krw")).intValue()
                            + safeAmount(record.getTotalAmount())
            );

            monthly.put(
                    "total_carbon_kg",
                    ((Number) monthly.get("total_carbon_kg")).doubleValue()
                            + safeCarbon(record.getTotalCarbonKg())
            );
        }

        // 영수증별 데이터
        List<Map<String, Object>> receiptData = new ArrayList<>();

        for (ConsumptionRecord record : records) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("record_id", record.getRecordId());
            data.put("merchant_name", record.getMerchantName());
            data.put("record_date", dateString(record.getRecordDate()));
            data.put("total_amount_krw", safeAmount(record.getTotalAmount()));
            data.put("total_carbon_kg", safeCarbon(record.getTotalCarbonKg()));
            receiptData.add(data);
        }

        summary.put("user_id", userId);
        summary.put("record_count", records.size());
        summary.put("total_amount_krw", totalAmount);
        summary.put("total_carbon_kg", totalCarbon);
        summary.put("category_carbon_summary", categoryCarbon);
        summary.put("category_amount_summary", categoryAmount);
        summary.put("highest_carbon_category", highestCategory(categoryCarbon));
        summary.put("current_month", YearMonth.now().toString());
        summary.put("monthly_summary", monthlySummary);
        summary.put("receipts", receiptData);
        summary.put("items", itemData);

        return summary;
    }

    // 품목 목록 생성
    private List<Map<String, Object>> buildItemData(
            List<ConsumptionItem> items,
            Map<Long, ConsumptionRecord> recordMap
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ConsumptionItem item : items) {
            Long recordId = item.getRecord().getRecordId();
            ConsumptionRecord record = recordMap.get(recordId);

            if (record == null) {
                continue;
            }

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("record_id", recordId);
            data.put("item_id", item.getItemId());
            data.put("item_name", item.getSourceMsg());
            data.put("merchant_name", record.getMerchantName());
            data.put("record_date", dateString(record.getRecordDate()));
            data.put("amount_krw", safeAmount(item.getAmount()));
            data.put("carbon_kg", safeCarbon(item.getCarbonKg()));
            data.put("main_category_id", item.getMainCategoryId());
            data.put("middle_category_id", item.getMiddleCategoryId());
            data.put("classify_stage", item.getClassifyStage());

            // 카테고리 이름은 OCR 원본에서 찾을 수 있을 때만 사용
            Map<String, Object> names = findCategoryNames(record, item);
            data.put("main_name", names.get("main_name"));
            data.put("middle_name", names.get("middle_name"));

            result.add(data);
        }

        return result;
    }

    private Map<String, Object> findCategoryNames(
            ConsumptionRecord record,
            ConsumptionItem item
    ) {
        Map<String, Object> result = new HashMap<>();

        if (record.getOcrData() == null || record.getOcrData().isBlank()) {
            return result;
        }

        try {
            Map<String, Object> ocr = objectMapper.readValue(
                    record.getOcrData(),
                    new TypeReference<Map<String, Object>>() {}
            );

            Object rawItems = ocr.get("item_results");

            if (!(rawItems instanceof List<?> itemResults)) {
                return result;
            }

            for (Object rawItem : itemResults) {
                if (!(rawItem instanceof Map<?, ?> row)) {
                    continue;
                }

                Object name = row.get("item_name");
                if (name == null) {
                    name = row.get("source_msg");
                }

                if (!Objects.equals(
                        Objects.toString(name, null),
                        item.getSourceMsg()
                )) {
                    continue;
                }

                Object rawCategory = row.get("category");

                if (!(rawCategory instanceof Map<?, ?> category)) {
                    continue;
                }

                result.put("main_name", category.get("main_name"));
                result.put("middle_name", category.get("middle_name"));
                break;
            }

        } catch (Exception e) {
            log.debug(
                    "OCR 카테고리 이름 추출 실패 (recordId={})",
                    record.getRecordId()
            );
        }

        return result;
    }

    private Map<String, Double> categoryCarbonSummary(
            List<ConsumptionItem> items
    ) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (ConsumptionItem item : items) {
            String key = categoryKey(item);

            result.merge(
                    key,
                    safeCarbon(item.getCarbonKg()),
                    Double::sum
            );
        }

        return result;
    }

    private Map<String, Integer> categoryAmountSummary(
            List<ConsumptionItem> items
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();

        for (ConsumptionItem item : items) {
            String key = categoryKey(item);

            result.merge(
                    key,
                    safeAmount(item.getAmount()),
                    Integer::sum
            );
        }

        return result;
    }

    private String categoryKey(ConsumptionItem item) {
        return item.getMainCategoryId() != null
                ? "대분류 ID " + item.getMainCategoryId()
                : "카테고리 미확인";
    }

    private String highestCategory(Map<String, Double> categoryCarbon) {
        return categoryCarbon.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private int safeAmount(Integer value) {
        return value != null ? value : 0;
    }

    private double safeCarbon(Float value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private String dateString(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        return new RestTemplate(factory);
    }
}
