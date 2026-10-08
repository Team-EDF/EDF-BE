package com.edf.teamedf.domain.challenge.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자의 Green Profile (설문 답변 + AI가 계산한 프로필).
 *
 * <p>AI 서버(POST /api/profile)의 응답 JSON을 그대로 보관한다. 필드 구조는 AI가 정하므로
 * BE는 해석하지 않고 전달만 한다. 사용자당 1건이며 설문을 다시 하면 덮어쓴다.</p>
 */
@Entity
@Table(name = "green_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_green_profile_user", columnNames = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GreenProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 설문 답변 JSON (예: {"transport":"car", ...}) */
    @Column(name = "survey_answers", columnDefinition = "TEXT")
    private String surveyAnswers;

    /** AI 프로필 응답 JSON 전체 */
    @Column(name = "profile_json", columnDefinition = "TEXT")
    private String profileJson;

    /** "survey" | "data" */
    @Column(name = "source", length = 20)
    private String source;

    /** 그린 유형 코드 (예: DLSA) */
    @Column(name = "type_code", length = 10)
    private String typeCode;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void update(String surveyAnswers, String profileJson, String source, String typeCode) {
        this.surveyAnswers = surveyAnswers;
        this.profileJson = profileJson;
        this.source = source;
        this.typeCode = typeCode;
        this.updatedAt = LocalDateTime.now();
    }
}
