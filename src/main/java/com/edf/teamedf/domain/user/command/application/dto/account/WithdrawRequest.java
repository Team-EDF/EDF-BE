package com.edf.teamedf.domain.user.command.application.dto.account;

/**
 * 회원 탈퇴 요청.
 *
 * @param password    더 이상 사용하지 않는다 (구버전 앱 호환을 위해 필드만 남겨 둠).
 * @param confirmText 탈퇴 의사 확인 문구("탈퇴합니다"). 모든 계정에서 필수.
 * @param reason      탈퇴 사유 (선택, 통계용).
 */
public record WithdrawRequest(
        String password,
        String confirmText,
        String reason
) {
}
