package com.edf.teamedf.domain.challenge.command.application.dto;

import java.time.LocalDate;

/**
 * 챌린지 사진 인증 결과.
 *
 * <p>통과/거절 모두 200으로 돌려주고 {@code verified}로 구분한다 (거절 사유는 {@code message}).
 * 사진 형식 문제(400)나 AI 서버 불가(503)는 HTTP 오류로 내려간다.</p>
 *
 * @param verified      인증 통과 여부
 * @param code          OK | RECEIPT_UNREADABLE | RECEIPT_TOO_OLD | NOT_CAFE | NO_TUMBLER | NO_MARK | DUPLICATE_RECEIPT ...
 * @param message       사용자에게 그대로 보여 줄 안내 문구
 * @param evidence      통과 근거 (PHOTO_AND_RECEIPT | PHOTO | RECEIPT_DISCOUNT | MARK_AND_RECEIPT)
 * @param justCompleted 이번 인증으로 챌린지가 완료됐는지
 * @param pointsAwarded 이번에 지급된 챌린지 완료 포인트 (완료되지 않았으면 0)
 * @param bonusPoints   이번 인증 보너스 포인트 (일일 한도를 넘었으면 0)
 * @param totalPoints   지급 후 내 누적 포인트
 */
public record VerifyChallengeResponse(
        boolean verified,
        String code,
        String message,
        String evidence,
        ReceiptSummary receipt,
        UserChallengeResponse challenge,
        boolean justCompleted,
        int pointsAwarded,
        int bonusPoints,
        long totalPoints,
        int previousLevel,
        int level,
        boolean leveledUp
) {

    public record ReceiptSummary(String merchantName, LocalDate paymentDate, Integer totalAmount) {
    }

    /** 인증이 거절된 경우 (진행도·포인트 변화 없음). */
    public static VerifyChallengeResponse rejected(
            String code, String message, ReceiptSummary receipt,
            UserChallengeResponse challenge, long totalPoints, int level) {
        return new VerifyChallengeResponse(
                false, code, message, null, receipt, challenge, false, 0, 0, totalPoints, level, level, false);
    }
}
