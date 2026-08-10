package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.security.auth.RefreshTokenStore;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.common.security.jwt.JwtTokenProvider;
import com.edf.teamedf.domain.user.command.application.dto.auth.LoginRequest;
import com.edf.teamedf.domain.user.command.application.dto.auth.SignUpRequest;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore tokenStore;
    private final AuthenticationManager authenticationManager;
    private final PhoneVerificationService phoneVerificationService;
    private final AddressValidatorService addressValidatorService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String verifiedEmail = emailVerificationService.consumeVerifiedEmail(request.emailVerificationToken());
        if (!verifiedEmail.equals(request.email())) {
            throw new IllegalArgumentException("인증된 이메일과 입력한 이메일이 일치하지 않습니다.");
        }

        String phone = phoneVerificationService.consumeVerifiedPhone(request.phoneVerificationToken());
        String validatedAddress = addressValidatorService.validateAndFormat(request.address());

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(phone)
                .address(validatedAddress)
                .addressDetail(request.addressDetail())
                .zipCode(request.zipCode())
                .role(request.role() != null ? request.role() : User.Role.MEMBER)
                .provider("local")
                .enabled(true)
                .build();

        userRepository.save(user);
    }

    public TokenPair login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String uuid = principal.uuid().toString();

        String accessToken = jwtTokenProvider.createAccessToken(uuid, Map.of(
                "email", principal.email() != null ? principal.email() : "",
                "name", principal.name() != null ? principal.name() : "",
                "role", principal.role() != null ? principal.role() : "MEMBER",
                "userId", principal.userId()
        ));
        String refreshToken = jwtTokenProvider.createRefreshToken(uuid);

        tokenStore.save(uuid, refreshToken, jwtTokenProvider.getRefreshExpSeconds());

        return new TokenPair(accessToken, buildRefreshCookie(refreshToken, jwtTokenProvider.getRefreshExpSeconds()));
    }

    public TokenPair refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || !jwtTokenProvider.validate(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        String uuid = jwtTokenProvider.getSubject(refreshToken);
        String stored = tokenStore.get(uuid);

        if (!refreshToken.equals(stored)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다.");
        }

        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(uuid, Map.of(
                "email", user.getEmail() != null ? user.getEmail() : "",
                "name", user.getName() != null ? user.getName() : "",
                "role", user.getRole().name(),
                "userId", user.getUserId()
        ));
        String newRefreshToken = jwtTokenProvider.createRefreshToken(uuid);

        tokenStore.save(uuid, newRefreshToken, jwtTokenProvider.getRefreshExpSeconds());

        return new TokenPair(newAccessToken, buildRefreshCookie(newRefreshToken, jwtTokenProvider.getRefreshExpSeconds()));
    }

    public ResponseCookie logout(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken != null && jwtTokenProvider.validate(refreshToken)) {
            tokenStore.delete(jwtTokenProvider.getSubject(refreshToken));
        }
        return buildRefreshCookie("", 0);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public TokenPair issueTokensForUser(User user) {
        String uuid = user.getUuid();
        String accessToken = jwtTokenProvider.createAccessToken(uuid, java.util.Map.of(
                "email", user.getEmail() != null ? user.getEmail() : "",
                "name", user.getName() != null ? user.getName() : "",
                "role", user.getRole() != null ? user.getRole().name() : "MEMBER",
                "userId", user.getUserId()
        ));
        String refreshToken = jwtTokenProvider.createRefreshToken(uuid);
        tokenStore.save(uuid, refreshToken, jwtTokenProvider.getRefreshExpSeconds());
        return new TokenPair(accessToken, buildRefreshCookie(refreshToken, jwtTokenProvider.getRefreshExpSeconds()));
    }

    public record TokenPair(String accessToken, ResponseCookie refreshCookie) {}
}
