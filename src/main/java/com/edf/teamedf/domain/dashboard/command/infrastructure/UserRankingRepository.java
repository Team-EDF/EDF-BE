package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.UserRanking;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRankingRepository extends JpaRepository<UserRanking, Long> {

    // 사용자 + 랭킹 타입 + 기간 + 기간 시작일로 단건 조회 (탄소 절감 점수 조회용)
    Optional<UserRanking> findByUser_UserIdAndRankingTypeAndRankingPeriodAndPeriodStart(
            Long userId, String rankingType, String rankingPeriod, LocalDate periodStart);

    /** 해당 기간에서 내 점수보다 높은 점수를 가진 사용자 수 (순위 재계산용). */
    @Query("""
            select count(r) from UserRanking r
            where r.rankingType = :rankingType
              and r.rankingPeriod = :rankingPeriod
              and r.periodStart = :periodStart
              and r.score > :score
            """)
    long countBetterScores(@Param("rankingType") String rankingType,
                           @Param("rankingPeriod") String rankingPeriod,
                           @Param("periodStart") LocalDate periodStart,
                           @Param("score") Float score);

    /** 해당 기간 상위 랭킹 목록 (리더보드). */
    @Query("""
            select r from UserRanking r
            join fetch r.user
            where r.rankingType = :rankingType
              and r.rankingPeriod = :rankingPeriod
              and r.periodStart = :periodStart
            order by r.score desc
            """)
    List<UserRanking> findLeaderboard(@Param("rankingType") String rankingType,
                                      @Param("rankingPeriod") String rankingPeriod,
                                      @Param("periodStart") LocalDate periodStart,
                                      Pageable pageable);

    /** 해당 기간 참여자 수. */
    long countByRankingTypeAndRankingPeriodAndPeriodStart(
            String rankingType, String rankingPeriod, LocalDate periodStart);
}
