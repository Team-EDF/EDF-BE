package com.edf.teamedf.domain.user.command.application.dto.address;

import jakarta.validation.constraints.NotBlank;

public record AddressValidateRequest(@NotBlank String address) {}
