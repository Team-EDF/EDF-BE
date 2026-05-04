package com.edf.teamedf.domain.user.command.application.dto.account;

import jakarta.validation.constraints.NotBlank;

public record FindEmailRequest(@NotBlank String phoneVerificationToken) {}
