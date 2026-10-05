package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.auth.AppOAuthLoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.AuthResponse;
import com.edf.teamedf.domain.user.command.application.dto.auth.LoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.NicknameCheckResponse;
import com.edf.teamedf.domain.user.command.application.dto.auth.SignUpRequest;
import com.edf.teamedf.domain.user.command.application.service.AppOAuth2Service;
import com.edf.teamedf.domain.user.command.application.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppOAuth2Service appOAuth2Service;

    @PostMapping("/signup")
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 닉네임 중복 확인
     * GET /auth/nickname/check?nickname=xxx
     */
    @GetMapping("/nickname/check")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        boolean available = authService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(new NicknameCheckResponse(available));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair pair = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/oauth2/app")
    public ResponseEntity<?> appSocialLogin(@Valid @RequestBody AppOAuthLoginRequest request) {
        AuthService.TokenPair pair;
        try {
            pair = appOAuth2Service.loginOrSignUpFromApp(
                    request.provider(),
                    request.token(),
                    Boolean.TRUE.equals(request.termsAgreed())
                            && Boolean.TRUE.equals(request.privacyAgreed())
                            && Boolean.TRUE.equals(request.ageConfirmed()));
        } catch (IllegalArgumentException e) {
            // 앱이 "CONSENT_REQUIRED" 를 보고 약관 동의 화면을 띄울 수 있도록 메시지를 그대로 내려준다.
            return ResponseEntity.badRequest().body(java.util.Map.of("message", String.valueOf(e.getMessage())));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        AuthService.TokenPair pair = authService.refresh(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var cookie = authService.logout(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
