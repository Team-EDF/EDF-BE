package com.edf.teamedf.domain.user.command.application.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AppOAuthLoginRequest(
        @NotBlank(message = "Provider는 필수 항목입니다.")
        String provider, // "google", "kakao", "naver"

        @NotBlank(message = "Token은 필수 항목입니다.")
        String token, // Access Token 또는 ID Token

        // 신규 소셜 가입 시 필수 동의 (기존 회원 로그인에서는 무시된다)
        Boolean termsAgreed,
        Boolean privacyAgreed,
        Boolean ageConfirmed // 만 14세 이상 확인
) {}
