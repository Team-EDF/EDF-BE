package com.edf.teamedf.domain.chat.application;

import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.edf.teamedf.domain.dashboard.command.application.service.DashboardService;
import com.edf.teamedf.domain.dashboard.command.application.dto.MonthlySpendingResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.CategoryCarbonRatioResponse;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final DashboardService dashboardService;

    public ChatResponse sendChatMessage(Long userId, ChatRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        String aiUrl = "http://ai:8000/api/feedback/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("user_id", userId);
        aiRequest.put("message", request.getMessage());

        MonthlySpendingResponse spending = dashboardService.getMonthlySpending(userId);
        List<CategoryCarbonRatioResponse> ratios = dashboardService.getCategoryCarbonRatios(userId);

        Map<String, Float> categoryCarbonSummary = new HashMap<>();
        for (CategoryCarbonRatioResponse r : ratios) {
            categoryCarbonSummary.put(r.categoryName(), r.categoryCarbon());
        }

        Map<String, Object> summary = new HashMap<>();
        if (spending != null) {
            summary.put("total_carbon_kg", spending.totalCarbon());
        } else {
            summary.put("total_carbon_kg", 0f);
        }
        summary.put("category_carbon_summary", categoryCarbonSummary);

        aiRequest.put("consumption_summary", summary);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

        try {
            // Wait! In main.py, the route is @app.post("/feedback/chat") not "/api/feedback/chat"
            aiUrl = "http://ai:8000/feedback/chat";
            Map<String, Object> aiResponse = restTemplate.postForObject(aiUrl, entity, Map.class);
            if (aiResponse != null) {
                Long chatId = null;
                if (aiResponse.get("chat_id") != null) {
                    chatId = Long.valueOf(aiResponse.get("chat_id").toString());
                }
                String feedback = (String) aiResponse.get("feedback");
                return new ChatResponse(chatId, feedback);
            }
        } catch (Exception e) {
            System.err.println("AI Chat Service error: " + e.getMessage());
            return new ChatResponse(null, "AI 서버와의 통신에 실패했습니다.");
        }

        return new ChatResponse(null, "AI가 응답하지 않습니다.");
    }
}
