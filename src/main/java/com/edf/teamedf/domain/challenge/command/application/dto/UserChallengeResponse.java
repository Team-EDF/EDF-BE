package com.edf.teamedf.domain.challenge.command.application.dto;

import com.edf.teamedf.domain.challenge.command.domain.UserChallenge;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이번 주 챌린지 한 건 (프론트 챌린지 화면용).
 *
 * @param checkedToday      오늘 이미 직접 체크했는지 (자율 체크는 하루 1회)
 * @param canCheckIn        지금 "체크하기"를 누를 수 있는지 (자율 체크 + 진행 중 + 오늘 미체크 + 직접 체크 한도 남음)
 * @param photoVerification 사진 인증 종류 (TUMBLER | LOW_CARBON), 사진 인증이 없으면 null
 * @param canVerify         "인증하기"를 누를 수 있는지 (사진 인증이 있는 챌린지. 완료 후에도 가능, 이때는 보너스만)
 * @param selfCheckLimit    직접 체크로 인정되는 주간 횟수 (null이면 제한 없음)
 * @param selfChecks        이번 주 직접 체크한 횟수
 * @param verifiedCount     이번 주 사진 인증한 횟수
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
        LocalDateTime completedAt,
        String photoVerification,
        boolean canVerify,
        Integer selfCheckLimit,
        int selfChecks,
        int verifiedCount
) {
    public static UserChallengeResponse of(UserChallenge c, boolean checkedToday, int selfChecks, int verifiedCount) {
        boolean selfLimitLeft = c.getSelfCheckLimit() == null || selfChecks < c.getSelfCheckLimit();
        boolean canCheckIn = UserChallenge.VERIFY_SELF.equals(c.getVerification())
                && !c.isCompleted() && !checkedToday && selfLimitLeft;
        boolean canVerify = c.getPhotoVerification() != null;
        return new UserChallengeResponse(
                c.getUserChallengeId(), c.getSlot(), c.getChallengeId(), c.getArea(), c.getAreaLabel(),
                c.getDifficulty(), c.getTitle(), c.getDescription(), c.getTargetCount(), c.getUnit(),
                c.getVerification(), c.getPoints(), c.getEstSavingKg(), c.getReason(), c.getReasonSource(),
                c.getStatus(), c.getProgressCount(), checkedToday, canCheckIn, c.getWeekStart(), c.getCompletedAt(),
                c.getPhotoVerification(), canVerify, c.getSelfCheckLimit(), selfChecks, verifiedCount);
    }
}
