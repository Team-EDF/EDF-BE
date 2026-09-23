package com.edf.teamedf.domain.news.command.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Long newsId;

    /** 기사 출처 (예: GREEN NEWS). */
    @Column(name = "source", length = 100, nullable = false)
    private String source;

    /** 출처 유형 표기 (예: 외부 기사). */
    @Column(name = "source_tag", length = 50)
    private String sourceTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "url", length = 500)
    private String url;

    /** 기사 발행 시각 (프론트에서 timeAgo 로 상대 시간 표시). */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Category {
        TRANSIT, CONSUMPTION, CARBON, POLICY
    }
}
