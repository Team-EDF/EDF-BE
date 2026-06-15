package com.edf.teamedf.domain.dashboard.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "consumption_records")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ConsumptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // pending / success / failed / manually_completed (card_statement는 null)
    @Column(name = "ocr_status", length = 30)
    private String ocrStatus;

    @Column(name = "ocr_error_message", length = 500)
    private String ocrErrorMessage;

    @Column(name = "ocr_data", columnDefinition = "TEXT")
    private String ocrData;

    @Column(name = "record_date")
    private LocalDate recordDate;

    // 분류 완료 후 집계
    @Column(name = "total_amount")
    private Integer totalAmount;

    // 분류 완료 후 집계
    @Column(name = "total_carbon_kg")
    private Float totalCarbonKg;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
