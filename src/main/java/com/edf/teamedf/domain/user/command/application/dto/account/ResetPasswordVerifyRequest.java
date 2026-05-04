package com.edf.teamedf.domain.user.command.application.dto.account;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordVerifyRequest(
        @NotBlank String emailVerificationToken,
        @NotBlank String phoneVerificationToken
) {}
