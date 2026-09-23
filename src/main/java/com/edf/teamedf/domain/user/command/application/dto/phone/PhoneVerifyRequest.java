package com.edf.teamedf.domain.user.command.application.dto.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PhoneVerifyRequest(
        @NotBlank @Pattern(regexp = "^01[016789]\\d{7,8}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phone,

        @NotBlank @Size(min = 6, max = 6, message = "인증번호는 6자리입니다.")
        String code
) {}
