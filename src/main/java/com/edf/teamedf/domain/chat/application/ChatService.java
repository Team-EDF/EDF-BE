package com.edf.teamedf.domain.chat.application;

import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final ConsumptionRecordRepository consumptionRecordRepository;

    public ChatResponse sendChatMessage(Long userId, ChatRequest request) {
        ConsumptionRecord record = consumptionRecordRepository.findById(request.getRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "소비 기록을 찾을 수 없습니다."));

        if (!record.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 소비 기록에 대해서만 피드백을 요청할 수 있습니다.");
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // AI의 FeedbackChatRequest 스키마: user_id(필수, >0), record_id(선택, >0), message(필수, 1~1000자)
        // user_id는 필수 필드이므로 반드시 함께 전달해야 한다 (누락 시 AI 서버에서 422 응답).
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("user_id", userId);
        aiRequest.put("record_id", request.getRecordId());
        aiRequest.put("message", request.getMessage());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

        try {
            Map<String, Object> aiResponse = restTemplate.postForObject(aiServiceUrl + "/feedback/chat", entity, Map.class);
            if (aiResponse != null) {
                Long chatId = aiResponse.get("chat_id") != null
                        ? Long.valueOf(aiResponse.get("chat_id").toString())
                        : null;
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
