package com.edf.teamedf.domain.community.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 커뮤니티 신고. 한 사용자는 같은 대상을 한 번만 신고할 수 있다.
 * targetId 는 targetType 에 따라 postId / commentId / userId 를 가리킨다.
 */
@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reporter_id", "target_type", "target_id"}),
        indexes = @Index(name = "idx_reports_status_created", columnList = "status, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20, nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 신고된 콘텐츠의 작성자 (USER 신고면 신고 대상 본인). 운영자가 사용자 단위로 모아 보기 위함. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** 신고가 발생한 게시글 (운영자가 맥락을 찾기 위한 참고값). */
    @Column(name = "post_id")
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 20, nullable = false)
    private Reason reason;

    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void process(Status status, String adminMemo, Long adminId) {
        this.status = status;
        this.adminMemo = adminMemo;
        this.processedBy = adminId;
        this.processedAt = status == Status.PENDING ? null : LocalDateTime.now();
    }

    public enum TargetType {
        POST, COMMENT, USER
    }

    public enum Reason {
        SPAM, ABUSE, HATE, SEXUAL, ILLEGAL, PRIVACY, OTHER
    }

    public enum Status {
        /** 접수됨 (처리 대기) */
        PENDING,
        /** 위반으로 판단해 조치함 */
        RESOLVED,
        /** 위반이 아니라고 판단해 반려함 */
        REJECTED
    }
}
