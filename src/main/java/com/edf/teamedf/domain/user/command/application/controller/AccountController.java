package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.account.FindEmailRequest;
import com.edf.teamedf.domain.user.command.application.dto.account.FindEmailResponse;
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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/find-email")
    public ResponseEntity<FindEmailResponse> findEmail(@Valid @RequestBody FindEmailRequest request) {
        String maskedEmail = accountService.findEmail(request.phoneVerificationToken());
        return ResponseEntity.ok(new FindEmailResponse(maskedEmail));
    }

    @PostMapping("/reset-password/verify")
    public ResponseEntity<ResetPasswordVerifyResponse> verifyForReset(@Valid @RequestBody ResetPasswordVerifyRequest request) {
        String resetToken = accountService.verifyForPasswordReset(
                request.emailVerificationToken(),
                request.phoneVerificationToken()
        );
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
