package com.edf.teamedf.domain.news.command.infrastructure;

import com.edf.teamedf.domain.news.command.domain.News;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 앱 최초 구동 시 환경 뉴스 샘플 데이터를 시딩한다.
 * (프론트엔드에 하드코딩되어 있던 NEWS_ITEMS 를 실제 데이터로 대체)
 */
@Component
@RequiredArgsConstructor
public class NewsDataInitializer implements CommandLineRunner {

    private final NewsRepository newsRepository;

    @Override
    public void run(String... args) {
        if (newsRepository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        newsRepository.save(News.builder()
                .source("GREEN NEWS")
                .sourceTag("외부 기사")
                .category(News.Category.TRANSIT)
                .title("도시 교통 탄소를 줄이기 위한 새로운 대중교통 정책")
                .content("대중교통 이용 확대, 교통 수요 관리와 탄소 감축 목표를 연결하는 최근 정책 흐름을 정리했습니다.")
                .url("https://www.me.go.kr")
                .publishedAt(now.minusHours(2))
                .build());

        newsRepository.save(News.builder()
                .source("CLIMATE DAILY")
                .sourceTag("외부 기사")
                .category(News.Category.CONSUMPTION)
                .title("소비 데이터로 보는 생활 속 탄소 발자국 변화")
                .content("식품과 생활용품 소비 패턴에 따라 개인 탄소 배출이 어떻게 달라지는지 데이터를 통해 살펴봅니다.")
                .url("https://www.keco.or.kr")
                .publishedAt(now.minusHours(4))
                .build());

        newsRepository.save(News.builder()
                .source("ECO TIMES")
                .sourceTag("외부 기사")
                .category(News.Category.CARBON)
                .title("대중교통 이용 증가가 도시 배출량에 미치는 영향")
                .content("승용차 이용을 대체하는 대중교통 이동이 도시 단위 온실가스 배출에 미치는 영향을 분석합니다.")
                .url("https://www.gir.go.kr")
                .publishedAt(now.minusDays(1))
                .build());

        newsRepository.save(News.builder()
                .source("EARTH POLICY")
                .sourceTag("외부 기사")
                .category(News.Category.POLICY)
                .title("2026 탄소중립 실천 포인트제 확대 개편안 발표")
                .content("친환경 대중교통 이용과 다회용기 이용에 대한 인센티브가 대폭 확대됩니다.")
                .url("https://www.cpoint.or.kr")
                .publishedAt(now.minusDays(2))
                .build());
    }
}
