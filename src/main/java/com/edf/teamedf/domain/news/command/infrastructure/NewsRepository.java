package com.edf.teamedf.domain.news.command.infrastructure;

import com.edf.teamedf.domain.news.command.domain.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {

    Page<News> findAllByOrderByPublishedAtDesc(Pageable pageable);

    Page<News> findAllByCategoryOrderByPublishedAtDesc(News.Category category, Pageable pageable);

    /** 스케줄러가 동일 기사(url)를 중복 저장하지 않도록 확인한다. */
    boolean existsByUrl(String url);
}
