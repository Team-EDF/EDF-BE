package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.UserRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserRankingRepository extends JpaRepository<UserRanking, Long> {

    // 사용자 + 랭킹 타입 + 기간 + 기간 시작일로 단건 조회 (탄소 절감 점수 조회용)
    Optional<UserRanking> findByUser_UserIdAndRankingTypeAndRankingPeriodAndPeriodStart(
            Long userId, String rankingType, String rankingPeriod, LocalDate periodStart);
}
