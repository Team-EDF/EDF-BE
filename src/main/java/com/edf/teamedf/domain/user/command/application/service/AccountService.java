package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneVerificationService phoneVerificationService;
    private final EmailVerificationService emailVerificationService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long RESET_TTL_SECONDS = 600;
    private static final String RESET_PREFIX = "reset:password:";

    // ── 이메일 찾기 ──────────────────────────────────────────
    public String findEmail(String phoneVerificationToken) {
        String phone = phoneVerificationService.consumeVerifiedPhone(phoneVerificationToken);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("해당 전화번호로 가입된 계정이 없습니다."));

        return maskEmail(user.getEmail());
    }

    // ── 비밀번호 재설정 1단계: 이메일 + 전화번호 인증 ─────────
    public String verifyForPasswordReset(String emailVerificationToken, String phoneVerificationToken) {
        String email = emailVerificationService.consumeVerifiedEmail(emailVerificationToken);
        String phone = phoneVerificationService.consumeVerifiedPhone(phoneVerificationToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다."));

        if (!phone.equals(user.getPhone())) {
            throw new IllegalArgumentException("이메일과 전화번호가 일치하는 계정이 없습니다.");
        }

        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RESET_PREFIX + resetToken, email, RESET_TTL_SECONDS, TimeUnit.SECONDS);

        return resetToken;
    }

    // ── 비밀번호 재설정 2단계: 새 비밀번호 설정 ──────────────
    @Transactional
    public void resetPassword(String resetToken, String newPassword, String newPasswordConfirm) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String email = redisTemplate.opsForValue().get(RESET_PREFIX + resetToken);
        if (email == null) {
            throw new IllegalArgumentException("비밀번호 재설정 세션이 만료되었습니다. 다시 인증해주세요.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.updatePassword(passwordEncoder.encode(newPassword));
        redisTemplate.delete(RESET_PREFIX + resetToken);
    }

    // ── 이메일 마스킹: test@example.com → te**@example.com ──
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return email;
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + domain;
    }
}
