package com.edf.teamedf.domain.challenge.command.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AI 서버의 Green Action API 호출 클라이언트.
 *
 * <p>POST /api/profile : 설문 답변 -> Green Profile<br>
 * POST /api/challenges/recommend : 프로필 -> 맞춤 챌린지 3개<br>
 * POST /api/challenges/verify : 사진(텀블러+영수증 / 저탄소 마크+영수증) -> 인증 판정 (multipart)<br>
 * POST /api/household/read-bill : 관리비/공과금 고지서 사진 -> 사용월과 전기·수도·가스·난방 값 (multipart)<br>
 * POST /api/household/carbon : 한 달 값 -> 가정 에너지 탄소 계산</p>
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

    /** 사진 인증은 AI가 사진을 읽느라 보통 2~8초(첫 호출은 더) 걸린다. */
    private static final int VERIFY_READ_TIMEOUT_MS = 40_000;

    private final RestTemplate restTemplate;
    private final RestTemplate verifyRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GreenAiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);

        SimpleClientHttpRequestFactory verifyFactory = new SimpleClientHttpRequestFactory();
        verifyFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        verifyFactory.setReadTimeout(VERIFY_READ_TIMEOUT_MS);
        this.verifyRestTemplate = new RestTemplate(verifyFactory);
    }

    /** 설문 답변(+ user_id)으로 Green Profile 생성. */
    public Map<String, Object> createProfile(Map<String, Object> body) {
        return post("/api/profile", body);
    }

    /** 프로필로 맞춤 챌린지 3개 추천. */
    public Map<String, Object> recommend(Map<String, Object> body) {
        return post("/api/challenges/recommend", body);
    }

    /**
     * 사진으로 챌린지 인증을 판정한다. 통과/거절은 응답(passed)으로 오고, 사진 문제(400)와 AI 불가(503)는 예외로 던진다.
     * 사진은 AI 서버에서도 저장하지 않는다.
     */
    public Map<String, Object> verify(String kind, List<MultipartFile> images) {
        return postImages("/api/challenges/verify", Map.of("kind", kind), images, "AI 인증");
    }

    /** 관리비/공과금 고지서 사진(1~3장)을 읽어 사용월과 전기·수도·가스·난방 값을 돌려받는다. 사진은 저장하지 않는다. */
    public Map<String, Object> readHouseholdBill(List<MultipartFile> images) {
        return postImages("/api/household/read-bill", Map.of(), images, "AI 고지서 판독");
    }

    /** 한 달 값(사용량/금액)으로 가정 에너지 탄소(kgCO2eq)와 항목별 내역을 계산한다. */
    public Map<String, Object> householdCarbon(Map<String, Object> values) {
        return post("/api/household/carbon", values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postImages(String path, Map<String, String> fields, List<MultipartFile> images, String label) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        fields.forEach(body::add);
        try {
            for (MultipartFile image : images) {
                if (image.isEmpty()) {
                    continue;
                }
                final String filename = image.getOriginalFilename() == null ? "image.jpg" : image.getOriginalFilename();
                body.add("images", new ByteArrayResource(image.getBytes()) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 읽지 못했어요. 다시 선택해 주세요.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            Map<String, Object> response = verifyRestTemplate.postForObject(
                    aiServiceUrl + path, new HttpEntity<>(body, headers), Map.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서버가 빈 응답을 돌려줬습니다.");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detailOf(e));
            }
            if (status == 503) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        label + "을(를) 잠시 쓸 수 없어요. 잠시 후 다시 시도해 주세요.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 서버 오류(" + status + ")");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    label + "이(가) 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 서버에 연결하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    /** AI 서버(FastAPI) 오류 본문의 {"detail": "..."} 문구를 꺼낸다. */
    private String detailOf(HttpStatusCodeException e) {
        try {
            String detail = objectMapper.readTree(e.getResponseBodyAsString()).path("detail").asText("");
            return detail.isBlank() ? "사진을 확인해 주세요." : detail;
        } catch (Exception ignored) {
            return "사진을 확인해 주세요.";
        }
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
