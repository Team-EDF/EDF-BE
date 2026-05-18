package com.edf.teamedf.domain.dashboard.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "user_rankings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ranking_id")
    private Long rankingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 탄소절감, 소비 절약 등
    @Column(name = "ranking_type", length = 50, nullable = false)
    private String rankingType;

    // daily, weekly, monthly
    @Column(name = "ranking_period", length = 20, nullable = false)
    private String rankingPeriod;

    // 추가됨: 랭킹 기간 시작일 (해당 기간 랭킹 조회 시 기간 특정에 필요)
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "score", nullable = false)
    private Float score;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "total_carbon", nullable = false)
    private Float totalCarbon;

    // 탄소 절감량 (kg)
    @Column(name = "total_saving", nullable = false)
    private Float totalSaving;

    @Column(name = "total_spending", nullable = false)
    private Integer totalSpending;
}
