package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.CategoryStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryStatRepository extends JpaRepository<CategoryStat, Long> {

    // 통합 통계 ID로 카테고리별 탄소 통계 목록 조회
    List<CategoryStat> findByIntegratedStat_StatId(Long statId);
}
