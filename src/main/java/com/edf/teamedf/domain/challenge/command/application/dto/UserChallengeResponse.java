package com.edf.teamedf.domain.challenge.command.application.dto;

import com.edf.teamedf.domain.challenge.command.domain.UserChallenge;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이번 주 챌린지 한 건 (프론트 챌린지 화면용).
 *
 * @param checkedToday 오늘 이미 진행 기록이 있는지 (자율 체크는 하루 1회라 true면 버튼 비활성화)
 * @param canCheckIn   지금 "체크하기"를 누를 수 있는지 (자율 체크 + 진행 중 + 오늘 미체크)
 */
public record UserChallengeResponse(
        Long id,
        Integer slot,
        String challengeId,
        String area,
        String areaLabel,
        Integer difficulty,
        String title,
        String description,
        Integer targetCount,
        String unit,
        String verification,
        Integer points,
        Float estSavingKg,
        String reason,
        String reasonSource,
        String status,
        Integer progressCount,
        boolean checkedToday,
        boolean canCheckIn,
        LocalDate weekStart,
        LocalDateTime completedAt
) {
    public static UserChallengeResponse of(UserChallenge c, boolean checkedToday) {
        boolean canCheckIn = UserChallenge.VERIFY_SELF.equals(c.getVerification())
                && !c.isCompleted() && !checkedToday;
        return new UserChallengeResponse(
                c.getUserChallengeId(), c.getSlot(), c.getChallengeId(), c.getArea(), c.getAreaLabel(),
                c.getDifficulty(), c.getTitle(), c.getDescription(), c.getTargetCount(), c.getUnit(),
                c.getVerification(), c.getPoints(), c.getEstSavingKg(), c.getReason(), c.getReasonSource(),
                c.getStatus(), c.getProgressCount(), checkedToday, canCheckIn, c.getWeekStart(), c.getCompletedAt());
    }
}
