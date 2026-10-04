package com.edf.teamedf.domain.challenge.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 챌린지 사진 인증 기록 (텀블러 + 영수증 / 저탄소 마크 + 영수증).
 *
 * <p>사진 자체는 저장하지 않고, AI가 읽은 영수증의 지문(날짜·시각·금액 해시)만 남긴다.
 * 지문은 DB 유니크라서 같은 영수증을 두 번(다른 챌린지, 다른 사용자 포함) 인증에 쓸 수 없다.
 * 인증 횟수에는 제한이 없고, 같은 영수증만 막는다.</p>
 */
@Entity
@Table(name = "challenge_verifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_challenge_verification_fingerprint", columnNames = "fingerprint")
        },
        indexes = {
                @Index(name = "idx_challenge_verification_user_created", columnList = "user_id, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChallengeVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_challenge_id", nullable = false)
    private UserChallenge userChallenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TUMBLER | LOW_CARBON */
    @Column(name = "kind", length = 20, nullable = false)
    private String kind;

    /** 영수증 지문 (AI가 만든 날짜·시각·금액 해시) */
    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "total_amount")
    private Integer totalAmount;

    /** PHOTO_AND_RECEIPT | PHOTO | RECEIPT_DISCOUNT | MARK_AND_RECEIPT */
    @Column(name = "evidence", length = 30)
    private String evidence;

    /** 이 인증으로 지급한 인증 보너스 포인트 (일일 한도를 넘었으면 0) */
    @Column(name = "bonus_points")
    private Integer bonusPoints;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
