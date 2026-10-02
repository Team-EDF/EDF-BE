package com.edf.teamedf.domain.dashboard.command.domain;

import jakarta.persistence.*;
import lombok.*;

// 영수증 품목별 분류 결과. AI 피드백(feedback_service.py)이 items 테이블을 직접 조회한다.
@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ConsumptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private ConsumptionRecord record;

    // main_category / middle_category 테이블은 AI가 관리하므로 ID만 보관한다.
    @Column(name = "main_category_id")
    private Long mainCategoryId;

    @Column(name = "middle_category_id")
    private Long middleCategoryId;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    // 품목명
    @Column(name = "source_msg", length = 500)
    private String sourceMsg;

    @Column(name = "amount")
    private Integer amount;

    @Column(name = "classify_stage")
    private Integer classifyStage;

    @Column(name = "carbon_kg")
    private Float carbonKg;
}
