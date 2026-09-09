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

    long countByUser_UserId(Long userId);

    long countByUser_UserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

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
