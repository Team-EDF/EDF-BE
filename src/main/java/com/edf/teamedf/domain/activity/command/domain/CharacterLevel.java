package com.edf.teamedf.domain.activity.command.domain;

/**
 * 캐릭터 성장(진화) 단계.
 *
 * <p>누적 절감 탄소량(kg CO₂)이 {@link #getThreshold()} 이상이 되면 해당 단계로 진화한다.
 * {@link #LV4} 가 최종 단계이며, 이후에는 레벨이 고정되고 누적 기록만 계속 쌓인다.
 *
 * <p><b>주의:</b> 임계값과 이름은 프론트 {@code src/constants/levels.js} 와 반드시 동일하게 유지할 것.
 */
public enum CharacterLevel {

    LV1(1, 0f, "작은 시작, 큰 변화", "지금, 지구를 위한 첫 걸음을 내딛어요!"),
    LV2(2, 10f, "조금 더 자란 마음", "지구를 위한 선택이 늘어나고 있어요."),
    LV3(3, 30f, "더 넓은 세상을 향해", "지속 가능한 내일을 위해 더 많은 실천을 하고 있어요."),
    LV4(4, 60f, "지구와 함께, 더 멀리", "이제는 변화를 만드는 리더로 성장하고 있어요.");

    public static final int MAX_LEVEL = 4;

    private final int level;
    private final float threshold;
    private final String title;
    private final String tagline;

    CharacterLevel(int level, float threshold, String title, String tagline) {
        this.level = level;
        this.threshold = threshold;
        this.title = title;
        this.tagline = tagline;
    }

    public int getLevel() {
        return level;
    }

    /** 이 단계에 도달하기 위해 필요한 누적 절감량 (kg CO₂). */
    public float getThreshold() {
        return threshold;
    }

    public String getTitle() {
        return title;
    }

    public String getTagline() {
        return tagline;
    }

    /** 누적 절감량으로 현재 단계를 구한다. */
    public static CharacterLevel of(float savedCarbon) {
        CharacterLevel current = LV1;
        for (CharacterLevel stage : values()) {
            if (savedCarbon >= stage.threshold) {
                current = stage;
            }
        }
        return current;
    }

    /** 누적 절감량으로 현재 레벨 번호(1~4)를 구한다. */
    public static int levelOf(float savedCarbon) {
        return of(savedCarbon).level;
    }

    /** 레벨 번호(1~4)로 단계를 구한다. 범위를 벗어나면 가장 가까운 단계로 보정한다. */
    public static CharacterLevel fromLevel(int level) {
        int clamped = Math.min(Math.max(level, 1), MAX_LEVEL);
        return values()[clamped - 1];
    }

    /** 다음 단계. 최종 단계라면 {@code null}. */
    public CharacterLevel next() {
        return level >= MAX_LEVEL ? null : values()[level];
    }

    public boolean isMax() {
        return level >= MAX_LEVEL;
    }

    /** 다음 단계까지 남은 절감량 (kg CO₂). 최종 단계라면 0. */
    public static float remainingToNext(float savedCarbon) {
        CharacterLevel next = of(savedCarbon).next();
        return next == null ? 0f : Math.max(next.threshold - savedCarbon, 0f);
    }
}
