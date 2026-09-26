package com.edf.teamedf.domain.news.command.infrastructure;

import com.edf.teamedf.domain.news.command.domain.News;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * 네이버 뉴스 검색 API로 카테고리별 최신 환경 뉴스를 가져와 News 테이블에 저장한다.
 *
 * - 매일 새벽 6시 자동 수집 (naver.news-api 키 미설정 시 NaverNewsClient 가 빈 결과를 반환하며 조용히 건너뜀)
 * - 앱 기동 시에도 1회 즉시 수집 (CommandLineRunner) — 기존 NewsDataInitializer 의 목업 시딩을 대체
 * - 동일 기사(url) 중복 저장 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsFetchScheduler implements CommandLineRunner {

    /** 카테고리별 검색 키워드. 서비스(친환경 소비·탄소중립·대중교통·환경정책) 성격에 맞춰 선정. */
    private static final Map<News.Category, String> CATEGORY_KEYWORDS = new EnumMap<>(News.Category.class);
    static {
        CATEGORY_KEYWORDS.put(News.Category.TRANSIT, "대중교통 탄소");
        CATEGORY_KEYWORDS.put(News.Category.CONSUMPTION, "친환경 소비");
        CATEGORY_KEYWORDS.put(News.Category.CARBON, "탄소중립");
        CATEGORY_KEYWORDS.put(News.Category.POLICY, "환경 정책");
    }

    /** 카테고리당 1회 수집 건수. */
    private static final int FETCH_SIZE = 10;

    /** 네이버 검색 API pubDate 포맷: "Wed, 23 Sep 2026 09:00:00 +0900" (RFC 1123). */
    private static final DateTimeFormatter PUB_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final NaverNewsClient naverNewsClient;
    private final NewsRepository newsRepository;
    private final ArticleImageExtractor articleImageExtractor;

    /** 앱 기동 시 1회 즉시 수집 (수집 실패해도 기동 자체는 막지 않는다). */
    @Override
    public void run(String... args) {
        fetchAndSaveAll();
    }

    /** 매일 새벽 6시 자동 수집. */
    @Scheduled(cron = "0 0 6 * * *")
    public void fetchDaily() {
        fetchAndSaveAll();
    }

    private void fetchAndSaveAll() {
        if (!naverNewsClient.isConfigured()) {
            log.info("네이버 뉴스 API 키가 아직 설정되지 않아 뉴스 자동 수집을 건너뜁니다.");
            return;
        }

        CATEGORY_KEYWORDS.forEach((category, keyword) -> {
            try {
                fetchAndSave(category, keyword);
            } catch (Exception e) {
                // 한 카테고리 수집 실패가 다른 카테고리 수집을 막지 않도록 개별 처리.
                log.error("{} 카테고리 뉴스 수집 실패: {}", category, e.getMessage());
            }
        });
    }

    private void fetchAndSave(News.Category category, String keyword) {
        var items = naverNewsClient.search(keyword, FETCH_SIZE);
        int savedCount = 0;

        for (var item : items) {
            if (item.url() == null || item.url().isBlank()) {
                continue;
            }
            if (newsRepository.existsByUrl(item.url())) {
                continue;
            }

            News news = News.builder()
                    .source(extractSource(item.url()))
                    .sourceTag("외부 기사")
                    .category(category)
                    .title(truncate(item.title(), 200))
                    .content(truncate(item.description(), 500))
                    .url(item.url())
                    // 네이버 검색 API 응답에 이미지가 없어 기사 페이지에서 직접 뽑는다.
                    // 실패하면 null 이고, 그 기사만 앱에서 일러스트로 대체된다.
                    .imageUrl(articleImageExtractor.extract(item.url()))
                    .publishedAt(parsePubDate(item.pubDate()))
                    .build();

            newsRepository.save(news);
            savedCount++;
        }

        log.info("{} 카테고리 뉴스 {}건 신규 저장 (검색어: {})", category, savedCount, keyword);
    }

    /** 기사 링크의 도메인을 출처 표기로 사용한다 (네이버 API 응답에 매체명 필드가 없음). */
    private String extractSource(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "뉴스";
        } catch (Exception e) {
            return "뉴스";
        }
    }

    private LocalDateTime parsePubDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return java.time.ZonedDateTime.parse(pubDate, PUB_DATE_FORMAT)
                    .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("뉴스 발행일 파싱 실패 (pubDate={}), 현재 시각으로 대체합니다.", pubDate);
            return LocalDateTime.now();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
