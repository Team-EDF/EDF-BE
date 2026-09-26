package com.edf.teamedf.domain.news.command.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기사 페이지에서 대표 이미지(Open Graph 썸네일) URL을 뽑아낸다.
 *
 * 네이버 뉴스 검색 API 응답에는 이미지 필드가 없어서, 수집한 기사 링크를 한 번 열어
 * &lt;meta property="og:image"&gt; 값을 읽는다. 언론사가 공유용으로 직접 노출하는 값이므로
 * 썸네일 용도로 쓰기에 적절하다.
 *
 * 수집 실패(타임아웃, 404, 메타 태그 없음 등)는 모두 null 로 처리한다.
 * 이미지가 없는 기사는 앱에서 카테고리 일러스트로 대체하면 되고,
 * 뉴스 수집 자체가 실패해서는 안 되기 때문이다.
 */
@Slf4j
@Component
public class ArticleImageExtractor {

    /** 본문 전체를 받지 않는다. og:image 는 &lt;head&gt; 안에 있으므로 앞부분만으로 충분하다. */
    private static final int MAX_READ_BYTES = 256 * 1024;

    /** DB 컬럼 길이와 맞춘다. 더 긴 URL 은 저장하지 않는다. */
    private static final int MAX_URL_LENGTH = 500;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** 일부 언론사는 기본 User-Agent 를 차단한다. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; GreenStepBot/1.0; +https://greenstep.app)";

    /** property / name 순서와 따옴표 종류가 매체마다 달라 둘 다 받아준다. */
    private static final Pattern[] IMAGE_PATTERNS = {
            compileMeta("og:image"),
            compileMeta("twitter:image"),
            compileMeta("twitter:image:src"),
    };

    private static Pattern compileMeta(String key) {
        return Pattern.compile(
                "<meta[^>]+(?:property|name)\\s*=\\s*[\"']" + Pattern.quote(key)
                        + "[\"'][^>]*content\\s*=\\s*[\"']([^\"']+)[\"']"
                        + "|<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*(?:property|name)\\s*=\\s*[\"']"
                        + Pattern.quote(key) + "[\"']",
                Pattern.CASE_INSENSITIVE);
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * @param articleUrl 기사 원문 URL
     * @return 대표 이미지 URL, 찾지 못하면 null
     */
    public String extract(String articleUrl) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(articleUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                return null;
            }

            String head = readHead(response.body());
            String found = findImageUrl(head);
            return found != null ? toAbsolute(found, articleUrl) : null;
        } catch (Exception e) {
            log.debug("기사 대표 이미지 추출 실패 (url={}): {}", articleUrl, e.getMessage());
            return null;
        }
    }

    /** 응답 앞부분만 읽고 연결을 닫는다. 본문이 큰 기사 페이지를 통째로 받지 않기 위해서다. */
    private String readHead(InputStream body) throws Exception {
        try (InputStream stream = body) {
            byte[] buffer = new byte[MAX_READ_BYTES];
            int total = 0;
            while (total < buffer.length) {
                int read = stream.read(buffer, total, buffer.length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            // URL 은 ASCII 범위라 인코딩 추측 없이 바이트를 그대로 문자로 읽어도 안전하다.
            return new String(buffer, 0, total, StandardCharsets.ISO_8859_1);
        }
    }

    private String findImageUrl(String html) {
        for (Pattern pattern : IMAGE_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /** //cdn.example.com/a.jpg, /img/a.jpg 같은 상대 경로를 기사 URL 기준으로 절대 경로로 만든다. */
    private String toAbsolute(String imageUrl, String articleUrl) {
        try {
            String resolved = URI.create(articleUrl).resolve(imageUrl).toString();
            if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) {
                return null;
            }
            return resolved.length() <= MAX_URL_LENGTH ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }
}
