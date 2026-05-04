package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.address.AddressValidateRequest;
import com.edf.teamedf.domain.user.command.application.dto.address.AddressValidateResponse;
import com.edf.teamedf.domain.user.command.application.service.AddressValidatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressValidatorService addressValidatorService;

    @PostMapping("/validate")
    public ResponseEntity<AddressValidateResponse> validate(@Valid @RequestBody AddressValidateRequest request) {
        String formatted = addressValidatorService.validateAndFormat(request.address());
        return ResponseEntity.ok(new AddressValidateResponse(formatted));
    }
}
