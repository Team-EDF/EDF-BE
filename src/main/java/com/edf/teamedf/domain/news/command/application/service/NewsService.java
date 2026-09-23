package com.edf.teamedf.domain.news.command.application.service;

import com.edf.teamedf.domain.news.command.application.dto.news.NewsResponse;
import com.edf.teamedf.domain.news.command.domain.News;
import com.edf.teamedf.domain.news.command.infrastructure.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsRepository newsRepository;

    /**
     * 환경 뉴스 목록 (최신순).
     *
     * @param category 뉴스 카테고리 (선택, null 이면 전체)
     */
    public Page<NewsResponse> getNews(News.Category category, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<News> news = category != null
                ? newsRepository.findAllByCategoryOrderByPublishedAtDesc(category, page)
                : newsRepository.findAllByOrderByPublishedAtDesc(page);
        return news.map(NewsResponse::from);
    }
}
