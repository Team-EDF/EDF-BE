package com.edf.teamedf.domain.community.command.application.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String content
) {}
