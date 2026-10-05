package com.edf.teamedf.domain.community.command.infrastructure;

import com.edf.teamedf.domain.community.command.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    /** 내 차단 목록 (차단된 사용자 정보까지 한 번에 조회). */
    @Query("""
            select b from UserBlock b join fetch b.blocked
            where b.blocker.userId = :blockerId
            order by b.createdAt desc
            """)
    List<UserBlock> findAllWithBlockedByBlockerId(@Param("blockerId") Long blockerId);

    @Query("select b.blocked.userId from UserBlock b where b.blocker.userId = :blockerId")
    List<Long> findBlockedUserIds(@Param("blockerId") Long blockerId);
}
