package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.IntegratedStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface IntegratedStatRepository extends JpaRepository<IntegratedStat, Long> {

    // 사용자 + 기간 타입 + 기간 시작일로 단건 조회 (이번달 소비 금액, 카테고리 비율 조회용)
    Optional<IntegratedStat> findByUser_UserIdAndPeriodTypeAndPeriodStart(
            Long userId, String periodType, LocalDate periodStart);
}
