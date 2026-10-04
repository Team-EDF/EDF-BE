package com.edf.teamedf.domain.challenge.command.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 챌린지 진행 기록 (하루 1회).
 *
 * <p>같은 챌린지에 같은 날짜로 두 번 들어갈 수 없다(DB 유니크). 자율 체크의 "하루 1회 제한"과
 * 대중교통 자동 인증의 "하루 1회만 센다"는 규칙을 DB 수준에서 보장한다.</p>
 */
@Entity
@Table(name = "challenge_check_ins", uniqueConstraints = {
        @UniqueConstraint(name = "uk_check_in_challenge_date", columnNames = {"user_challenge_id", "check_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChallengeCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_in_id")
    private Long checkInId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_challenge_id", nullable = false)
    private UserChallenge userChallenge;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /** MANUAL(직접 체크) | AUTO_TRANSIT(대중교통 인증 연동) */
    @Column(name = "method", length = 20)
    private String method;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
