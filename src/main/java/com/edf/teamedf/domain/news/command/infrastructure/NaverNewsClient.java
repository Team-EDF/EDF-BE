package com.edf.teamedf.domain.news.command.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * NAVER API HUB 뉴스 검색 클라이언트.
 * (https://api.ncloud-docs.com/docs/naver-api-hub-search-news)
 *
 * naver.news-api.client-id / client-secret 이 설정되지 않으면
 * (application.yml 의 NAVER_API_HUB_CLIENT_ID / NAVER_API_HUB_CLIENT_SECRET 미설정)
 * 호출을 시도하지 않고 빈 결과를 반환한다.
 */
@Slf4j
@Component
public class NaverNewsClient {

    @Value("${naver.news-api.url}")
    private String searchUrl;

    @Value("${naver.news-api.client-id:}")
    private String clientId;

    @Value("${naver.news-api.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * 주어진 키워드로 네이버 뉴스를 검색해 최신순 상위 {@code display}건을 가져온다.
     * API 키 미설정, 호출 실패, 빈 응답 등 모든 실패 상황에서 빈 리스트를 반환한다
     * (뉴스 수집 실패가 앱 기동이나 다른 스케줄에 영향을 주지 않도록).
     */
    @SuppressWarnings("unchecked")
    public List<NaverNewsItem> search(String query, int display) {
        if (!isConfigured()) {
            log.warn("네이버 뉴스 API 키가 설정되지 않아 '{}' 뉴스 수집을 건너뜁니다. " +
                    "NAVER_API_HUB_CLIENT_ID / NAVER_API_HUB_CLIENT_SECRET 환경변수를 설정하세요.", query);
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromUriString(searchUrl)
                .queryParam("query", query)
                .queryParam("display", display)
                .queryParam("sort", "date")
                .queryParam("format", "json")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
        headers.set("X-NCP-APIGW-API-KEY", clientSecret);

        try {
            Map<String, Object> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();

            Object rawItems = response != null ? response.get("items") : null;
            if (!(rawItems instanceof List<?> items)) {
                return Collections.emptyList();
            }

            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, String>) item)
                    .map(item -> {
                        String originalLink = item.get("originallink");
                        String link = (originalLink != null && !originalLink.isBlank())
                                ? originalLink : item.get("link");
                        return new NaverNewsItem(
                                stripHtml(item.get("title")),
                                stripHtml(item.get("description")),
                                link,
                                item.get("pubDate"));
                    })
                    .toList();
        } catch (Exception e) {
            log.error("네이버 뉴스 API 호출 실패 (query={}): {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 네이버 검색 API는 검색어와 일치하는 부분을 &lt;b&gt; 태그와 HTML 엔티티로 감싸서 반환한다. */
    private String stripHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'");
    }

    public record NaverNewsItem(String title, String description, String url, String pubDate) {
    }
}
