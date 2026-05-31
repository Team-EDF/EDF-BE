package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.config.CoolSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final CoolSmsService coolSmsService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long OTP_TTL_SECONDS      = 180;   // OTP 유효시간 3분
    private static final long VERIFIED_TTL_SECONDS  = 600;  // 인증 완료 토큰 유효시간 10분

    private static final String OTP_PREFIX      = "phone:otp:";
    private static final String VERIFIED_PREFIX = "phone:verified:";

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── 1단계: OTP 발송 ──────────────────────────────────────────
    public void sendOtp(String phoneNumber) {
        String otp = generateOtp();

        redisTemplate.opsForValue().set(
                OTP_PREFIX + phoneNumber, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);

        coolSmsService.sendSms(phoneNumber,
                "[EDF] 인증번호 [" + otp + "]를 입력해 주세요. (3분 이내 유효)");
    }

    // ── 2단계: OTP 검증 후 verificationToken 발급 ────────────────
    public String verifyOtpAndIssueToken(String phoneNumber, String inputOtp) {
        String stored = redisTemplate.opsForValue().get(OTP_PREFIX + phoneNumber);

        if (stored == null) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해 주세요.");
        }
        if (!stored.equals(inputOtp)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        redisTemplate.delete(OTP_PREFIX + phoneNumber); // OTP 소진

        String verificationToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                VERIFIED_PREFIX + verificationToken, phoneNumber,
                VERIFIED_TTL_SECONDS, TimeUnit.SECONDS);

        return verificationToken;
    }

    // ── 내부 사용: 인증 완료된 전화번호 꺼내기 (이메일 찾기·비밀번호 재설정) ──
    public String consumeVerifiedPhone(String verificationToken) {
        String phone = redisTemplate.opsForValue().get(VERIFIED_PREFIX + verificationToken);
        if (phone == null) {
            throw new IllegalArgumentException("전화번호 인증이 만료되었거나 유효하지 않습니다.");
        }
        redisTemplate.delete(VERIFIED_PREFIX + verificationToken);
        return phone;
    }

    // ── 6자리 OTP 생성 ─────────────────────────────────────────
    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
