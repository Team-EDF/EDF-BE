package com.edf.teamedf.domain.user.command.application.dto.account;

/**
 * 회원 탈퇴 요청.
 *
 * @param password    비밀번호 재확인. 로컬 가입(비밀번호가 있는) 계정은 필수.
 * @param confirmText 소셜 로그인처럼 비밀번호가 없는 계정에서 쓰는 확인 문구("탈퇴합니다").
 * @param reason      탈퇴 사유 (선택, 통계용).
 */
public record WithdrawRequest(
        String password,
        String confirmText,
        String reason
) {
}
