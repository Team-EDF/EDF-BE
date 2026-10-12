package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.auth.AppOAuthLoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.AuthResponse;
import com.edf.teamedf.domain.user.command.application.dto.auth.LoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.NicknameCheckResponse;
import com.edf.teamedf.domain.user.command.application.dto.auth.SignUpRequest;
import com.edf.teamedf.domain.user.command.application.service.AppOAuth2Service;
import com.edf.teamedf.domain.user.command.application.service.AuthService;
import com.edf.teamedf.common.security.ratelimit.ClientIp;
import com.edf.teamedf.common.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppOAuth2Service appOAuth2Service;
    private final RateLimiter rateLimiter;

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
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname,
                                                               HttpServletRequest httpRequest) {
        rateLimiter.consume("nickname-check:ip:" + ClientIp.of(httpRequest), 60, Duration.ofMinutes(1),
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        boolean available = authService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(new NicknameCheckResponse(available));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthService.TokenPair pair = authService.login(request, ClientIp.of(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/oauth2/app")
    public ResponseEntity<AuthResponse> appSocialLogin(@Valid @RequestBody AppOAuthLoginRequest request,
                                                       HttpServletRequest httpRequest) {
        // 토큰 검증마다 Google 을 호출하므로 IP 당 횟수를 제한한다.
        rateLimiter.consume("social-login:ip:" + ClientIp.of(httpRequest), 30, Duration.ofMinutes(10),
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        // 실패는 ResponseStatusException 으로 400/401/403/409 와 message 가 내려간다.
        // 앱은 message 의 "CONSENT_REQUIRED" 를 보고 약관 동의 화면을 띄운다.
        AuthService.TokenPair pair = appOAuth2Service.loginOrSignUpFromApp(
                request.provider(),
                request.token(),
                Boolean.TRUE.equals(request.termsAgreed())
                        && Boolean.TRUE.equals(request.privacyAgreed())
                        && Boolean.TRUE.equals(request.ageConfirmed()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        AuthService.TokenPair pair;
        try {
            pair = authService.refresh(request);
        } catch (IllegalArgumentException e) {
            // 만료·불일치·없는 리프레시 토큰은 서버 오류(500)가 아니라 "다시 로그인 필요"(401)로 알려 준다.
            // 앱은 이 401로 갱신 거절(로그인 화면)과 서버 장애(로그인 유지)를 구분한다.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
