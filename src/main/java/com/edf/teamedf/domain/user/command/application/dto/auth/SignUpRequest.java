package com.edf.teamedf.domain.user.command.application.dto.auth;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String passwordConfirm,
        @NotBlank String name,
        @NotBlank String nickname,
        @NotNull LocalDate birthDate,
        @NotNull User.Gender gender,
        @NotBlank String address,
        String addressDetail,
        String zipCode,
        User.Role role,
        // 앱 가입 흐름에서 휴대폰 인증 단계가 빠졌으므로 선택값이다 (있으면 인증된 번호를 저장).
        String phoneVerificationToken,
        @NotBlank String emailVerificationToken,
        @NotNull Boolean termsAgreed,
        @NotNull Boolean privacyAgreed,
        Boolean marketingAgreed
) {}
