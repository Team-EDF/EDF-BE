package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, Long> {

    // 특정 기간 내 일별 탄소 배출량 합계 조회 (주간 그래프용)
    @Query("SELECT r.recordDate, COALESCE(SUM(r.totalCarbonKg), 0.0) " +
           "FROM ConsumptionRecord r " +
           "WHERE r.user.userId = :userId " +
           "  AND r.recordDate BETWEEN :from AND :to " +
           "GROUP BY r.recordDate " +
           "ORDER BY r.recordDate")
    List<Object[]> findDailyCarbonByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<ConsumptionRecord> findByImageUrl(String imageUrl);
}
