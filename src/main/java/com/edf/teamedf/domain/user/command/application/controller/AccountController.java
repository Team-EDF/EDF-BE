package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.account.ResetPasswordRequest;
import com.edf.teamedf.domain.user.command.application.dto.account.ResetPasswordVerifyRequest;
import com.edf.teamedf.domain.user.command.application.dto.account.ResetPasswordVerifyResponse;
import com.edf.teamedf.domain.user.command.application.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 재설정 (이메일 인증 기반). 전화번호로 이메일 찾기(/auth/find-email)는 전화번호를 쓰지 않아 제거했다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/reset-password/verify")
    public ResponseEntity<ResetPasswordVerifyResponse> verifyForReset(@Valid @RequestBody ResetPasswordVerifyRequest request) {
        String resetToken = accountService.verifyForPasswordReset(request.emailVerificationToken());
        return ResponseEntity.ok(new ResetPasswordVerifyResponse(resetToken));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(
                request.resetToken(),
                request.newPassword(),
                request.newPasswordConfirm()
        );
        return ResponseEntity.ok().build();
    }
}
