package com.edf.teamedf.domain.news.command.application.controller;

import com.edf.teamedf.domain.news.command.application.dto.news.NewsResponse;
import com.edf.teamedf.domain.news.command.application.service.NewsService;
import com.edf.teamedf.domain.news.command.domain.News;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * 환경 뉴스 목록.
     *
     * @param category 뉴스 카테고리 (TRANSIT | CONSUMPTION | CARBON | POLICY, 선택)
     */
    @GetMapping
    public ResponseEntity<Page<NewsResponse>> getNews(
            @RequestParam(required = false) News.Category category,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.getNews(category, pageable));
    }
}
