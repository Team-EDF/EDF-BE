package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.security.auth.RefreshTokenStore;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 비밀번호 재설정. 전화번호 인증은 쓰지 않으므로 <b>이메일 인증</b>만으로 본인을 확인한다.
 *
 * <ol>
 *   <li>POST /auth/email/send → /auth/email/verify 로 emailVerificationToken 을 받는다.</li>
 *   <li>POST /auth/reset-password/verify { emailVerificationToken } → resetToken (10분)</li>
 *   <li>POST /auth/reset-password { resetToken, newPassword, newPasswordConfirm }</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RefreshTokenStore refreshTokenStore;

    private static final long RESET_TTL_SECONDS = 600;
    private static final String RESET_PREFIX = "reset:password:";

    // ── 비밀번호 재설정 1단계: 이메일 인증 확인 ─────────────────
    public String verifyForPasswordReset(String emailVerificationToken) {
        String email = emailVerificationService.consumeVerifiedEmail(emailVerificationToken);

        List<User> users = userRepository.findAllByEmailIgnoreCase(email);
        if (users.size() != 1 || users.get(0).isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 이메일로 가입된 계정이 없습니다.");
        }

        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RESET_PREFIX + resetToken, users.get(0).getUuid(), RESET_TTL_SECONDS, TimeUnit.SECONDS);
        return resetToken;
    }

    // ── 비밀번호 재설정 2단계: 새 비밀번호 설정 ──────────────
    @Transactional
    public void resetPassword(String resetToken, String newPassword, String newPasswordConfirm) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        String uuid = redisTemplate.opsForValue().get(RESET_PREFIX + resetToken);
        if (uuid == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호 재설정 세션이 만료되었습니다. 다시 인증해주세요.");
        }

        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        user.updatePassword(passwordEncoder.encode(newPassword));
        redisTemplate.delete(RESET_PREFIX + resetToken);
        // 비밀번호가 바뀌었으니 다른 기기의 로그인(리프레시 토큰)은 끊는다.
        refreshTokenStore.delete(uuid);
    }
}
