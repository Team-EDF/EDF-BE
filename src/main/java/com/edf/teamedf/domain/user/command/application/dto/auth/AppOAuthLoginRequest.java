package com.edf.teamedf.domain.user.command.application.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AppOAuthLoginRequest(
        @NotBlank(message = "Provider는 필수 항목입니다.")
        String provider, // "google", "kakao", "naver"

        @NotBlank(message = "Token은 필수 항목입니다.")
        String token // Access Token 또는 ID Token
) {}
