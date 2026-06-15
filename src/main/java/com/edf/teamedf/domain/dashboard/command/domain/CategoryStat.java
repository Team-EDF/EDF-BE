package com.edf.teamedf.domain.dashboard.command.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "category_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Setter
public class CategoryStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cat_stat_id")
    private Long catStatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stat_id", nullable = false)
    private IntegratedStat integratedStat;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_name", length = 50)
    private String categoryName;

    @Column(name = "category_carbon", nullable = false)
    private Float categoryCarbon;

    @Column(name = "category_spending", nullable = false)
    private Integer categorySpending;

    // 전체 탄소 대비 해당 카테고리 비율 (%)
    @Column(name = "percentage")
    private Float percentage;
}
