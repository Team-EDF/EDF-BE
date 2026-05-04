package com.edf.teamedf.domain.user.command.application.dto.auth;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String passwordConfirm,
        @NotBlank String name,
        @NotBlank String address,
        String addressDetail,
        String zipCode,
        User.Role role,
        @NotBlank String phoneVerificationToken,
        @NotBlank String emailVerificationToken
) {}
