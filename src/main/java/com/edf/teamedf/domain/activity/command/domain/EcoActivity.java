package com.edf.teamedf.domain.activity.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자의 친환경 활동 인증 기록.
 */
@Entity
@Table(name = "eco_activities", indexes = {
        @Index(name = "idx_eco_activity_user_created", columnList = "user_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EcoActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private EcoCategory category;

    /** 인증 사진 URL (업로드된 이미지 경로 또는 외부 URL) */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "comment", length = 500)
    private String comment;

    /** 이 활동으로 절감한 CO2 (kg) */
    @Column(name = "saved_carbon", nullable = false)
    private Float savedCarbon;

    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned;

    /** 인식된 오브젝트 명칭. AI 판정 결과가 있으면 그 값, 없으면 카테고리 기본값 */
    @Column(name = "detection_name", length = 100)
    private String detectionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private Status status = Status.APPROVED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING, APPROVED, REJECTED
    }
}
