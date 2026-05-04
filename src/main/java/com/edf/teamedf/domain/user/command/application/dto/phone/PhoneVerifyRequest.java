package com.edf.teamedf.domain.user.command.application.dto.phone;

import jakarta.validation.constraints.NotBlank;

public record PhoneVerifyRequest(@NotBlank String idToken) {}
