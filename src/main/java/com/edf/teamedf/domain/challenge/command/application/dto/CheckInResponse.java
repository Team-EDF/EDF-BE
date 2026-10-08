package com.edf.teamedf.domain.challenge.command.application.dto;

/**
 * 챌린지 체크 결과.
 *
 * @param justCompleted 이번 체크로 목표를 채워 완료됐는지
 * @param pointsAwarded 이번에 지급된 포인트 (완료되지 않았으면 0)
 * @param totalPoints   지급 후 내 누적 포인트 (친환경 활동 포인트 포함)
 * @param previousLevel 체크 전 캐릭터 레벨
 * @param level         체크 후 캐릭터 레벨 (탄소 + 포인트 하이브리드 기준)
 * @param leveledUp     이번 체크로 캐릭터 레벨이 올랐는지
 */
public record CheckInResponse(
        UserChallengeResponse challenge,
        boolean justCompleted,
        int pointsAwarded,
        long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp
) {
}
