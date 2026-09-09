package com.edf.teamedf.domain.notification.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    /** 알림을 받는 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "message", length = 500)
    private String message;

    /** 알림 탭 시 이동할 대상 종류 (POST / ACTIVITY / PROFILE ...) */
    @Column(name = "link_type", length = 30)
    private String linkType;

    @Column(name = "link_id")
    private Long linkId;

    /** 알림을 발생시킨 사용자 이름 (있을 경우) */
    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markAsRead() {
        this.isRead = true;
    }
}
