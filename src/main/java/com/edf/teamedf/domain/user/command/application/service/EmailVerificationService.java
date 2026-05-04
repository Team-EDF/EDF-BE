package com.edf.teamedf.domain.user.command.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long OTP_TTL_SECONDS = 300;        // 5분
    private static final long VERIFIED_TTL_SECONDS = 600;   // 10분
    private static final String OTP_PREFIX = "email:otp:";
    private static final String VERIFIED_PREFIX = "email:verified:";

    public void sendOtp(String email) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        sendMail(email, otp);
    }

    public String verifyOtp(String email, String code) {
        String stored = redisTemplate.opsForValue().get(OTP_PREFIX + email);

        if (stored == null) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!stored.equals(code)) {
            throw new IllegalArgumentException("인증번호가 올바르지 않습니다.");
        }

        redisTemplate.delete(OTP_PREFIX + email);

        String verificationToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFIED_PREFIX + verificationToken, email, VERIFIED_TTL_SECONDS, TimeUnit.SECONDS);

        return verificationToken;
    }

    public String consumeVerifiedEmail(String verificationToken) {
        String email = redisTemplate.opsForValue().get(VERIFIED_PREFIX + verificationToken);
        if (email == null) {
            throw new IllegalArgumentException("이메일 인증이 만료되었거나 유효하지 않습니다.");
        }
        redisTemplate.delete(VERIFIED_PREFIX + verificationToken);
        return email;
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private void sendMail(String to, String otp) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(to);
            helper.setSubject("[EDF] 이메일 인증번호 안내");
            helper.setText(buildHtml(otp), true);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    private String buildHtml(String otp) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px;border:1px solid #e5e7eb;border-radius:8px">
                  <h2 style="color:#111827">이메일 인증</h2>
                  <p style="color:#6b7280">아래 인증번호를 입력해주세요. 인증번호는 5분간 유효합니다.</p>
                  <div style="font-size:36px;font-weight:bold;letter-spacing:8px;text-align:center;padding:24px 0;color:#4f46e5">
                    %s
                  </div>
                  <p style="color:#9ca3af;font-size:12px">본인이 요청하지 않은 경우 이 이메일을 무시하세요.</p>
                </div>
                """.formatted(otp);
    }
}
