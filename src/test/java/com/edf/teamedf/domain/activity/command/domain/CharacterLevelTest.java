package com.edf.teamedf.domain.activity.command.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterLevelTest {

    @Test
    void resolvesEveryEvolutionBoundaryWhenBothConditionsAreMet() {
        assertThat(CharacterLevel.of(0f, 0)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(9.99f, 600)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(10f, 600)).isEqualTo(CharacterLevel.LV2);
        assertThat(CharacterLevel.of(29.99f, 1800)).isEqualTo(CharacterLevel.LV2);
        assertThat(CharacterLevel.of(30f, 1800)).isEqualTo(CharacterLevel.LV3);
        assertThat(CharacterLevel.of(59.99f, 3600)).isEqualTo(CharacterLevel.LV3);
        assertThat(CharacterLevel.of(60f, 3600)).isEqualTo(CharacterLevel.LV4);
    }

    @Test
    void staysOnPreviousLevelWhenOnlyOneConditionIsMet() {
        // 탄소만 충분: 포인트가 모자라면 올라가지 않는다
        assertThat(CharacterLevel.of(10f, 599)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(100f, 0)).isEqualTo(CharacterLevel.LV1);
        // 포인트만 충분: 탄소가 모자라면 올라가지 않는다
        assertThat(CharacterLevel.of(9.9f, 5000)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(29.9f, 5000)).isEqualTo(CharacterLevel.LV2);
        // 높은 단계의 한쪽만 채운 경우는 그 아래 단계에 머문다
        assertThat(CharacterLevel.of(30f, 1799)).isEqualTo(CharacterLevel.LV2);
        assertThat(CharacterLevel.of(59f, 3600)).isEqualTo(CharacterLevel.LV3);
    }

    @Test
    void reportsRemainingCarbonAndPointsUntilNextEvolution() {
        assertThat(CharacterLevel.remainingCarbonToNext(7.5f, 100)).isEqualTo(2.5f);
        assertThat(CharacterLevel.remainingPointsToNext(7.5f, 100)).isEqualTo(500L);
        // 탄소는 이미 채웠고 포인트만 남은 경우
        assertThat(CharacterLevel.remainingCarbonToNext(35f, 0)).isZero();
        assertThat(CharacterLevel.remainingPointsToNext(35f, 0)).isEqualTo(600L);
        assertThat(CharacterLevel.remainingCarbonToNext(60f, 3600)).isZero();
        assertThat(CharacterLevel.remainingPointsToNext(60f, 3600)).isZero();
    }
}
