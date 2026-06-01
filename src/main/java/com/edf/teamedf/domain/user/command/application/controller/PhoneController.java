package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.phone.PhoneSendRequest;
import com.edf.teamedf.domain.user.command.application.dto.phone.PhoneVerifyRequest;
import com.edf.teamedf.domain.user.command.application.dto.phone.PhoneVerifyResponse;
import com.edf.teamedf.domain.user.command.application.service.PhoneVerificationService;
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

    /**
     * 1단계: SMS 인증번호 발송
     * POST /auth/phone/send
     * Body: { "phoneNumber": "01012345678" }
     */
    @PostMapping("/send")
    public ResponseEntity<Void> send(@Valid @RequestBody PhoneSendRequest request) {
        phoneVerificationService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok().build();
    }

    /**
     * 2단계: 인증번호 검증 → verificationToken 발급
     * POST /auth/phone/verify
     * Body: { "phoneNumber": "01012345678", "code": "123456" }
     */
    @PostMapping("/verify")
    public ResponseEntity<PhoneVerifyResponse> verify(@Valid @RequestBody PhoneVerifyRequest request) {
        String verificationToken = phoneVerificationService.verifyOtpAndIssueToken(
                request.phoneNumber(), request.code());
        return ResponseEntity.ok(new PhoneVerifyResponse(verificationToken));
    }
}
