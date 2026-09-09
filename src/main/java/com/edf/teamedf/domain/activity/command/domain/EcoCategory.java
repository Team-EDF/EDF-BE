package com.edf.teamedf.domain.activity.command.domain;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * 친환경 활동 카테고리와 카테고리별 탄소 절감량/포인트 기준표.
 *
 * 절감량·포인트는 규칙 기반(rule-based)으로 고정되어 있으며,
 * 이미지 인식/판정은 별도 AI 서비스의 책임 영역이다.
 */
@Getter
public enum EcoCategory {

    TUMBLER("텀블러 사용", "다회용 텀블러", 2.0f, 100),
    TRANSIT("대중교통 이용", "대중교통 영수증", 4.5f, 220),
    RECYCLING("분리수거", "재활용 배출 품목", 1.2f, 60),
    ENERGY("대기전력 차단", "멀티탭 스위치 꺼짐", 3.0f, 150),
    SHOPPING_BAG("장바구니 사용", "다회용 장바구니", 1.5f, 80),
    VEGETARIAN("채식 식단", "채식 한 끼", 2.5f, 130);

    private final String displayName;
    private final String detectionName;
    private final float savedCarbon;
    private final int points;

    EcoCategory(String displayName, String detectionName, float savedCarbon, int points) {
        this.displayName = displayName;
        this.detectionName = detectionName;
        this.savedCarbon = savedCarbon;
        this.points = points;
    }

    public static EcoCategory from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "카테고리는 필수입니다.");
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 카테고리입니다: " + raw));
    }
}
