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
    VEGETARIAN("채식 식단", "채식 한 끼", 2.5f, 130),

    /**
     * Green Action 챌린지 완료 보상용 카테고리 (사용자가 직접 인증하는 카테고리가 아님).
     * 포인트는 챌린지마다 달라서 기본값 0이고, 지급 시 챌린지의 포인트를 기록한다.
     * 절감량은 가정치라서 기록하지 않는다(0).
     */
    CHALLENGE("챌린지 완료", "챌린지 보상", 0f, 0),

    /**
     * 가정 에너지(관리비) 절감 보상용 카테고리 (사용자가 직접 인증하는 카테고리가 아님).
     * 고지서로 인증된 달끼리 비교해서 전기·수도·가스 사용량을 줄이면 포인트를 지급하고 이 카테고리로 기록한다.
     */
    HOUSEHOLD("생활 절감", "관리비 절감 보상", 0f, 0);

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

    /** 사용자가 직접 인증할 수 없고 서버가 보상으로만 기록하는 카테고리 (인증 화면 목록에서 숨기고 직접 인증은 막는다). */
    public boolean isSystemOnly() {
        return this == CHALLENGE || this == HOUSEHOLD;
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
