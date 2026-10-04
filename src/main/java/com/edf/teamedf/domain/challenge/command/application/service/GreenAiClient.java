package com.edf.teamedf.domain.challenge.command.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * AI 서버의 Green Action API 호출 클라이언트.
 *
 * <p>POST /api/profile : 설문 답변 -> Green Profile<br>
 * POST /api/challenges/recommend : 프로필 -> 맞춤 챌린지 3개</p>
 *
 * <p>AI 서버는 외부에 열려 있지 않고 BE만 호출한다. 호출이 실패하면 502로 원인을 돌려준다.</p>
 */
@Component
public class GreenAiClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    /** /recommend 는 문구 생성(LLM)이 들어가 1~3초 걸리므로 넉넉히 둔다. */
    private static final int READ_TIMEOUT_MS = 12_000;

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public GreenAiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 설문 답변(+ user_id)으로 Green Profile 생성. */
    public Map<String, Object> createProfile(Map<String, Object> body) {
        return post("/api/profile", body);
    }

    /** 프로필로 맞춤 챌린지 3개 추천. */
    public Map<String, Object> recommend(Map<String, Object> body) {
        return post("/api/challenges/recommend", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            Map<String, Object> response = restTemplate.postForObject(aiServiceUrl + path, body, Map.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서버가 빈 응답을 돌려줬습니다.");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            String detail = e.getResponseBodyAsString();
            if (detail != null && detail.length() > 200) {
                detail = detail.substring(0, 200);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 서버 오류(" + e.getStatusCode().value() + "): " + detail);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 서버에 연결하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
