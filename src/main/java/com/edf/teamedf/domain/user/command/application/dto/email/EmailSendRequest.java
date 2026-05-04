package com.edf.teamedf.domain.user.command.application.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailSendRequest(@NotBlank @Email String email) {}
