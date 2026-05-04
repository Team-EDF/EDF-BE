package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.phone.PhoneVerifyRequest;
import com.edf.teamedf.domain.user.command.application.dto.phone.PhoneVerifyResponse;
import com.edf.teamedf.domain.user.command.application.service.PhoneVerificationService;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/phone")
@RequiredArgsConstructor
public class PhoneController {

    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/verify")
    public ResponseEntity<PhoneVerifyResponse> verify(@Valid @RequestBody PhoneVerifyRequest request) {
        try {
            String verificationToken = phoneVerificationService.verifyAndIssueToken(request.idToken());
            return ResponseEntity.ok(new PhoneVerifyResponse(verificationToken));
        } catch (FirebaseAuthException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
