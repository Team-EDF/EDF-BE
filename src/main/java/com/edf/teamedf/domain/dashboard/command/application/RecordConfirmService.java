package com.edf.teamedf.domain.dashboard.command.application;

import com.edf.teamedf.domain.dashboard.command.domain.CategoryStat;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.domain.IntegratedStat;
import com.edf.teamedf.domain.dashboard.command.infrastructure.CategoryStatRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.IntegratedStatRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class RecordConfirmService {

    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final IntegratedStatRepository integratedStatRepository;
    private final CategoryStatRepository categoryStatRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 이미지 저장을 AI(Python)가 담당하게 되면서 영수증은 더 이상 UploadedImage 행을 갖지 않는다.
    // consumption_records.record_id를 기준으로 직접 확인한다.
    public void confirmByRecordId(Long recordId, Long userId) {
        ConsumptionRecord record = consumptionRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "소비 기록을 찾을 수 없습니다."));

        if (!record.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 소비 기록만 확인할 수 있습니다.");
        }

        if (!"WAITING_CONFIRM".equals(record.getOcrStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "확인 대기 상태(WAITING_CONFIRM)인 기록이 아닙니다.");
        }

        try {
            Map<String, Object> response = objectMapper.readValue(record.getOcrData(), new TypeReference<>() {});
            User user = record.getUser();

            Float totalCarbonKg = record.getTotalCarbonKg();
            Integer totalAmount = record.getTotalAmount();

            java.time.LocalDate firstDayOfMonth = record.getRecordDate().withDayOfMonth(1);
            IntegratedStat monthlyStat = integratedStatRepository
                    .findByUser_UserIdAndPeriodTypeAndPeriodStart(user.getUserId(), "MONTHLY", firstDayOfMonth)
                    .orElse(null);

            if (monthlyStat == null) {
                monthlyStat = IntegratedStat.builder()
                        .user(user)
                        .periodType("MONTHLY")
                        .periodStart(firstDayOfMonth)
                        .totalCarbon(totalCarbonKg != null ? totalCarbonKg : 0f)
                        .totalSpending(totalAmount != null ? totalAmount : 0)
                        .build();
            } else {
                monthlyStat.setTotalCarbon(monthlyStat.getTotalCarbon() + (totalCarbonKg != null ? totalCarbonKg : 0f));
                monthlyStat.setTotalSpending(monthlyStat.getTotalSpending() + (totalAmount != null ? totalAmount : 0));
            }
            monthlyStat = integratedStatRepository.save(monthlyStat);

            List<Map<String, Object>> itemResults = (List<Map<String, Object>>) response.get("item_results");
            Map<String, Object> merchantCategory = (Map<String, Object>) response.get("merchant_category");

            if (itemResults != null && !itemResults.isEmpty()) {
                for (Map<String, Object> item : itemResults) {
                    Map<String, Object> category = (Map<String, Object>) item.get("category");
                    if (category == null) continue;

                    Long catId = category.get("category_id") != null ? ((Number) category.get("category_id")).longValue() : 0L;
                    String catName = (String) category.get("main_name");
                    if (catName == null) catName = "미분류";

                    Float carbon = item.get("carbon_kg") != null ? ((Number) item.get("carbon_kg")).floatValue() : 0f;
                    Integer spending = item.get("amount_krw") != null ? ((Number) item.get("amount_krw")).intValue() : 0;

                    updateOrCreateCategoryStat(monthlyStat, catId, catName, carbon, spending);
                }
            } else if (merchantCategory != null) {
                Long catId = merchantCategory.get("category_id") != null ? ((Number) merchantCategory.get("category_id")).longValue() : 0L;
                String catName = (String) merchantCategory.get("main_name");
                if (catName == null) catName = "미분류";

                Float carbon = response.get("merchant_carbon_kg") != null ? ((Number) response.get("merchant_carbon_kg")).floatValue() : 0f;
                Integer spending = totalAmount != null ? totalAmount : 0;

                updateOrCreateCategoryStat(monthlyStat, catId, catName, carbon, spending);
            }

            record.setOcrStatus("SUCCESS");
            consumptionRecordRepository.save(record);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to confirm record: " + e.getMessage());
        }
    }

    private void updateOrCreateCategoryStat(IntegratedStat monthlyStat, Long categoryId, String categoryName, Float carbon, Integer spending) {
        CategoryStat catStat = categoryStatRepository.findByIntegratedStat_StatId(monthlyStat.getStatId())
                .stream()
                .filter(cs -> cs.getCategoryId().equals(categoryId))
                .findFirst()
                .orElse(null);

        if (catStat == null) {
            catStat = CategoryStat.builder()
                    .integratedStat(monthlyStat)
                    .categoryId(categoryId)
                    .categoryName(categoryName)
                    .categoryCarbon(carbon)
                    .categorySpending(spending)
                    .percentage(0f)
                    .build();
        } else {
            catStat.setCategoryCarbon(catStat.getCategoryCarbon() + carbon);
            catStat.setCategorySpending(catStat.getCategorySpending() + spending);
        }
        categoryStatRepository.save(catStat);
    }
}
