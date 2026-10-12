package com.edf.teamedf.domain.activity.command.infrastructure;

import com.edf.teamedf.domain.activity.command.domain.EcoActivity;
import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EcoActivityRepository extends JpaRepository<EcoActivity, Long> {

    Page<EcoActivity> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<EcoActivity> findAllByUser_UserIdAndCategoryOrderByCreatedAtDesc(
            Long userId, EcoCategory category, Pageable pageable);

    List<EcoActivity> findAllByUser_UserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime after);

    @Query("""
            select a from EcoActivity a where a.user.userId = :userId
              and (:category is null or a.category = :category)
              and a.createdAt >= :start and a.createdAt < :end
            order by a.createdAt desc, a.activityId desc
            """)
    Page<EcoActivity> findForMonth(@Param("userId") Long userId,
            @Param("category") EcoCategory category, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, Pageable pageable);

    @Query("select coalesce(sum(a.savedCarbon), 0) from EcoActivity a where a.user.userId = :userId and a.createdAt >= :start and a.createdAt < :end")
    Double sumSavedCarbonInPeriod(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select count(a), coalesce(sum(a.savedCarbon), 0) from EcoActivity a
            where a.user.userId = :userId and a.category = :category
              and a.detectionName = :detectionName and a.createdAt >= :start and a.createdAt < :end
            """)
    List<Object[]> transitStats(@Param("userId") Long userId, @Param("category") EcoCategory category,
            @Param("detectionName") String detectionName, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    java.util.Optional<EcoActivity> findByUser_UserIdAndTripSessionId(Long userId, String tripSessionId);

    long countByUser_UserId(Long userId);

    long countByUser_UserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    long countByUser_UserIdAndCategoryAndCreatedAtGreaterThanEqual(Long userId, EcoCategory category, LocalDateTime from);

    long countByUser_UserIdAndCategoryInAndCreatedAtGreaterThanEqual(
            Long userId, java.util.Collection<EcoCategory> categories, LocalDateTime from);

    /** 이미 인증된 이동 중 시간이 겹치는 것이 있는지 (같은 경로 재전송·겹치는 이동 중복 인증 방지). */
    @Query("""
            select count(a) > 0 from EcoActivity a
            where a.user.userId = :userId
              and a.category = :category
              and a.tripStartMs is not null and a.tripEndMs is not null
              and a.tripStartMs < :endMs and a.tripEndMs > :startMs
            """)
    boolean existsOverlappingTrip(@Param("userId") Long userId,
                                  @Param("category") EcoCategory category,
                                  @Param("startMs") long startMs,
                                  @Param("endMs") long endMs);

    @Query("select coalesce(sum(a.savedCarbon), 0) from EcoActivity a where a.user.userId = :userId")
    Double sumSavedCarbonByUserId(@Param("userId") Long userId);

    @Query("select coalesce(sum(a.pointsEarned), 0) from EcoActivity a where a.user.userId = :userId")
    Long sumPointsByUserId(@Param("userId") Long userId);

    @Query("""
            select a.category, count(a), coalesce(sum(a.savedCarbon), 0)
            from EcoActivity a
            where a.user.userId = :userId
            group by a.category
            order by count(a) desc
            """)
    List<Object[]> aggregateByCategory(@Param("userId") Long userId);
}
