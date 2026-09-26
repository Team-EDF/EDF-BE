package com.edf.teamedf.domain.activity.command.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterLevelTest {

    @Test
    void resolvesEveryEvolutionBoundary() {
        assertThat(CharacterLevel.of(0f)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(9.99f)).isEqualTo(CharacterLevel.LV1);
        assertThat(CharacterLevel.of(10f)).isEqualTo(CharacterLevel.LV2);
        assertThat(CharacterLevel.of(29.99f)).isEqualTo(CharacterLevel.LV2);
        assertThat(CharacterLevel.of(30f)).isEqualTo(CharacterLevel.LV3);
        assertThat(CharacterLevel.of(59.99f)).isEqualTo(CharacterLevel.LV3);
        assertThat(CharacterLevel.of(60f)).isEqualTo(CharacterLevel.LV4);
    }

    @Test
    void reportsRemainingCarbonUntilNextEvolution() {
        assertThat(CharacterLevel.remainingToNext(7.5f)).isEqualTo(2.5f);
        assertThat(CharacterLevel.remainingToNext(20f)).isEqualTo(10f);
        assertThat(CharacterLevel.remainingToNext(60f)).isZero();
    }
}
