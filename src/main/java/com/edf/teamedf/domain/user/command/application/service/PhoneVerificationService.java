package com.edf.teamedf.domain.user.command.application.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final FirebaseAuth firebaseAuth;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long TTL_SECONDS = 600; // 10분
    private static final String KEY_PREFIX = "phone:verified:";

    public String verifyAndIssueToken(String idToken) throws FirebaseAuthException {
        FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
        String phoneNumber = decoded.getClaims().get("phone_number") != null
                ? decoded.getClaims().get("phone_number").toString()
                : null;

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("전화번호 인증 토큰이 아닙니다.");
        }

        String verificationToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + verificationToken, phoneNumber, TTL_SECONDS, TimeUnit.SECONDS);

        return verificationToken;
    }

    public String consumeVerifiedPhone(String verificationToken) {
        String phone = redisTemplate.opsForValue().get(KEY_PREFIX + verificationToken);
        if (phone == null) {
            throw new IllegalArgumentException("전화번호 인증이 만료되었거나 유효하지 않습니다.");
        }
        redisTemplate.delete(KEY_PREFIX + verificationToken);
        return phone;
    }
}
