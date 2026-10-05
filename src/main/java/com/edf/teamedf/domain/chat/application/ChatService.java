package com.edf.teamedf.domain.chat.application;

import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /**
     * 참고 영수증이 있는 대화에서 질문 앞에 붙이는 문구.
     *
     * AI 서버(feedback_service._should_use_consumption_context)는 질문에 "내 소비내역" 같은 표현이
     * 없으면 소비 요약을 프롬프트에서 빼고 "소비 내역을 찾을 수 없다"고 답한다.
     * AI 서버를 수정하지 않고 이를 피하기 위해, AI의 consumption_reference_keywords 에 들어 있는
     * "내 소비내역" 을 문구로 붙인다. AI의 키워드 목록이 바뀌면 이 상수만 맞춰 고치면 된다.
     */
    static final String CONSUMPTION_CONTEXT_PREFIX = "[내 소비내역 기준] ";

    /** AI의 message 최대 길이(1000자)에서 문구 길이를 뺀 값이 아니라, 컨트롤러 검증 한도(900자)와 맞춘다. */
    public static final int MAX_MESSAGE_LENGTH = 900;

    private static final String NO_RECEIPT_MESSAGE =
            "아직 등록한 영수증이 없어요. 영수증을 등록하면 내 소비 기록을 바탕으로 알려드릴게요.";

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    // Gemini 응답이 느릴 수 있어 읽기 제한은 길게 둔다.
    private static final int READ_TIMEOUT_MS = 60_000;

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final ConsumptionRecordRepository consumptionRecordRepository;

    public ChatResponse sendChatMessage(Long userId, ChatRequest request) {
        Long recordId = request.getRecordId();

        if (recordId != null) {
            ConsumptionRecord record = consumptionRecordRepository.findById(recordId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "소비 기록을 찾을 수 없습니다."));

            if (!record.getUser().getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 소비 기록에 대해서만 피드백을 요청할 수 있습니다.");
            }
        } else if (!consumptionRecordRepository.existsByUser_UserId(userId)) {
            // 영수증이 하나도 없으면 AI가 404를 내므로, 호출하지 않고 안내한다.
            return new ChatResponse(null, request.getConversationId(), NO_RECEIPT_MESSAGE);
        }

        String message = recordId != null
                ? CONSUMPTION_CONTEXT_PREFIX + request.getMessage()
                : request.getMessage();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // AI의 FeedbackChatRequest 스키마: user_id(필수, >0), conversation_id(선택, >0), record_id(선택, >0), message(필수, 1~1000자)
        // user_id는 필수 필드이므로 반드시 함께 전달해야 한다 (누락 시 AI 서버에서 422 응답).
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("user_id", userId);
        if (recordId != null) {
            aiRequest.put("record_id", recordId);
        }
        // 같은 대화방을 이어가야 AI가 이전 대화(chat_history)를 불러올 수 있다.
        // 안 보내면 매 메시지마다 새 대화방이 생겨 후속 질문의 문맥이 사라진다.
        if (request.getConversationId() != null) {
            aiRequest.put("conversation_id", request.getConversationId());
        }
        aiRequest.put("message", message);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

        Map<String, Object> aiResponse;
        try {
            aiResponse = restTemplate().postForObject(aiServiceUrl + "/feedback/chat", entity, Map.class);
        } catch (HttpClientErrorException e) {
            log.warn("AI chat 4xx (userId={}, recordId={}, conversationId={}): {} {}",
                    userId, recordId, request.getConversationId(), e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "소비 기록 또는 대화방을 찾을 수 없습니다.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI가 요청을 처리하지 못했습니다.");
        } catch (Exception e) {
            // 타임아웃·연결 실패·5xx. 화면이 에러와 다시 시도를 보여줄 수 있게 502로 알린다.
            log.error("AI chat service error (userId={}, recordId={}, conversationId={})",
                    userId, recordId, request.getConversationId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서버와의 통신에 실패했습니다.");
        }

        if (aiResponse == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI가 응답하지 않습니다.");
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

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
