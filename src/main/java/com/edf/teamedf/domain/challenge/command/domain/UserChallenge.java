package com.edf.teamedf.domain.challenge.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자에게 이번 주에 부여된 챌린지.
 *
 * <p>AI 추천 응답의 챌린지 정보를 부여 시점 그대로 복사해 보관한다(스냅샷).
 * 카탈로그가 나중에 바뀌어도 이미 부여된 챌린지의 기준(횟수/포인트)은 그대로 유지된다.</p>
 */
@Entity
@Table(name = "user_challenges", indexes = {
        @Index(name = "idx_user_challenge_user_week", columnList = "user_id, week_start")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserChallenge {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final String VERIFY_AUTO_TRANSIT = "AUTO_TRANSIT";
    public static final String VERIFY_SELF = "SELF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_challenge_id")
    private Long userChallengeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 이 챌린지가 속한 주의 월요일 */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /** 추천 순서 1~3 */
    @Column(name = "slot", nullable = false)
    private Integer slot;

    @Column(name = "challenge_id", length = 20, nullable = false)
    private String challengeId;

    @Column(name = "area", length = 20)
    private String area;

    @Column(name = "area_label", length = 20)
    private String areaLabel;

    @Column(name = "difficulty")
    private Integer difficulty;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    @Column(name = "unit", length = 10)
    private String unit;

    /** AUTO_TRANSIT(대중교통 GPS 자동 인증) | SELF(직접 체크) */
    @Column(name = "verification", length = 20, nullable = false)
    private String verification;

    @Column(name = "points", nullable = false)
    private Integer points;

    /** 사진 인증 종류: TUMBLER | LOW_CARBON | null(사진 인증 없음). 기존 행은 null */
    @Column(name = "photo_verification", length = 20)
    private String photoVerification;

    /** 사진 인증이 있는 챌린지의 자율 체크 주간 인정 횟수. null이면 제한 없음 */
    @Column(name = "self_check_limit")
    private Integer selfCheckLimit;

    /** 서버 교차 검증: NO_SHOPPING_RECEIPT(그날 쇼핑 영수증이 있으면 무구매로 인정 안 함) | null */
    @Column(name = "cross_check", length = 30)
    private String crossCheck;

    public static final String CROSS_NO_SHOPPING_RECEIPT = "NO_SHOPPING_RECEIPT";

    /** 예상 절감량(가정치). 근거가 없으면 null */
    @Column(name = "est_saving_kg")
    private Float estSavingKg;

    @Column(name = "reason", length = 500)
    private String reason;

    /** "llm" | "fallback" */
    @Column(name = "reason_source", length = 20)
    private String reasonSource;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @Column(name = "progress_count", nullable = false)
    @Builder.Default
    private Integer progressCount = 0;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /** 진행도를 다시 정한다 (무구매 교차 검증으로 취소된 체크를 반영할 때). 완료된 챌린지에는 쓰지 않는다. */
    public void resetProgress(int count) {
        this.progressCount = Math.max(0, count);
    }

    /** 진행도를 1 올리고, 목표에 도달하면 완료 처리한다. 방금 완료됐으면 true. */
    public boolean addProgress() {
        this.progressCount = this.progressCount + 1;
        if (!isCompleted() && this.progressCount >= this.targetCount) {
            this.status = STATUS_COMPLETED;
            this.completedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }
}
