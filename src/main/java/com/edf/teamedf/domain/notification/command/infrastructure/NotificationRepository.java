package com.edf.teamedf.domain.notification.command.infrastructure;

import com.edf.teamedf.domain.notification.command.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends org.springframework.data.jpa.repository.JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findAllByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_UserIdAndIsReadFalse(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.user.userId = :userId and n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.user.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
