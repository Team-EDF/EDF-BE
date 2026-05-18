package com.edf.teamedf.domain.dashboard.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "integrated_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class IntegratedStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 목표 탄소 절감 테이블의 goal_id 참조
    @Column(name = "goal_id")
    private Long goalId;

    // DAILY, WEEKLY, MONTHLY
    @Column(name = "period_type", length = 20, nullable = false)
    private String periodType;

    // 추가됨: 통계 기간의 시작일 (월별/주간/일별 조회 시 기간 특정에 필요)
    @Column(name = "period_start")
    private LocalDate periodStart;

    // 해당 기간 총 탄소 배출량
    @Column(name = "total_carbon", nullable = false)
    private Float totalCarbon;

    // 해당 기간 총 소비 금액
    @Column(name = "total_spending", nullable = false)
    private Integer totalSpending;
}
