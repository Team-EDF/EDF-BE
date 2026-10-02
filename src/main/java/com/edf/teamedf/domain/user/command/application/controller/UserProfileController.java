package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.user.command.application.dto.account.WithdrawRequest;
import com.edf.teamedf.domain.user.command.application.dto.profile.MeResponse;
import com.edf.teamedf.domain.user.command.application.dto.profile.ProfileUpdateRequest;
import com.edf.teamedf.domain.user.command.application.service.AuthService;
import com.edf.teamedf.domain.user.command.application.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 본인 프로필 조회/수정. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userProfileService.getMe(AuthUtils.requireUserId(principal)));
    }

    @PatchMapping("/me")
    public ResponseEntity<MeResponse> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateMe(AuthUtils.requireUserId(principal), request));
    }

    /**
     * 회원 탈퇴. 본인 확인 후 계정을 익명화하고 refresh token 쿠키까지 비운다.
     * 이미 발급된 access token 은 만료될 때까지 유효하므로 클라이언트도 토큰을 지워야 한다.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) WithdrawRequest request,
            HttpServletRequest httpRequest) {
        userProfileService.withdraw(AuthUtils.requireUserId(principal), request);
        ResponseCookie clearedCookie = authService.logout(httpRequest);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .build();
    }

    /** PATCH 를 지원하지 않는 클라이언트를 위한 별칭. */
    @PutMapping("/me")
    public ResponseEntity<MeResponse> replaceMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateMe(AuthUtils.requireUserId(principal), request));
    }
}
