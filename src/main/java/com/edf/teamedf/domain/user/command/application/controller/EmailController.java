package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.email.EmailSendRequest;
import com.edf.teamedf.domain.user.command.application.dto.email.EmailVerifyRequest;
import com.edf.teamedf.domain.user.command.application.dto.email.EmailVerifyResponse;
import com.edf.teamedf.domain.user.command.application.service.EmailVerificationService;
import com.edf.teamedf.common.security.ratelimit.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send")
    public ResponseEntity<Void> send(@Valid @RequestBody EmailSendRequest request,
                                     HttpServletRequest httpRequest) {
        emailVerificationService.sendOtp(request.email(), ClientIp.of(httpRequest));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<EmailVerifyResponse> verify(@Valid @RequestBody EmailVerifyRequest request,
                                                      HttpServletRequest httpRequest) {
        String verificationToken = emailVerificationService.verifyOtp(request.email(), request.code(), ClientIp.of(httpRequest));
        return ResponseEntity.ok(new EmailVerifyResponse(verificationToken));
    }
}
