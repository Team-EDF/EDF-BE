package com.edf.teamedf.domain.user.command.application.dto.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneSendRequest(
        @NotBlank @Pattern(regexp = "^01[016789]\\d{7,8}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phoneNumber
) {}
