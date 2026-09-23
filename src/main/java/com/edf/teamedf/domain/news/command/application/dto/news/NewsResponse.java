package com.edf.teamedf.domain.news.command.application.dto.news;

import com.edf.teamedf.domain.news.command.domain.News;

import java.time.LocalDateTime;

public record NewsResponse(
        Long newsId,
        String source,
        String sourceTag,
        News.Category category,
        String title,
        String content,
        String url,
        LocalDateTime publishedAt
) {

    public static NewsResponse from(News news) {
        return new NewsResponse(
                news.getNewsId(),
                news.getSource(),
                news.getSourceTag(),
                news.getCategory(),
                news.getTitle(),
                news.getContent(),
                news.getUrl(),
                news.getPublishedAt()
        );
    }
}
