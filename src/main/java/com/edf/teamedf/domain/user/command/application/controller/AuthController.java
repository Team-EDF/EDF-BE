package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.auth.AppOAuthLoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.AuthResponse;
import com.edf.teamedf.domain.user.command.application.dto.auth.LoginRequest;
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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair pair = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, pair.refreshCookie().toString())
                .body(new AuthResponse(pair.accessToken()));
    }

    @PostMapping("/oauth2/app")
    public ResponseEntity<AuthResponse> appSocialLogin(@Valid @RequestBody AppOAuthLoginRequest request) {
        AuthService.TokenPair pair = appOAuth2Service.loginOrSignUpFromApp(request.provider(), request.token());
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
