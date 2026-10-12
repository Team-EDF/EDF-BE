package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.security.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 이메일 인증번호(6자리) 발송·확인.
 *
 * <p>무차별 대입·메일 폭탄 방지</p>
 * <ul>
 *   <li>발송: 같은 이메일은 60초에 1번, 1시간에 5번 / 같은 IP 는 1시간에 20번</li>
 *   <li>확인: 같은 인증번호로 5번 틀리면 인증번호를 폐기한다(다시 받아야 함) / 같은 IP 는 1시간에 60번</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimiter rateLimiter;

    private static final long OTP_TTL_SECONDS = 300;        // 5분
    private static final long VERIFIED_TTL_SECONDS = 600;   // 10분
    private static final String OTP_PREFIX = "email:otp:";
    private static final String ATTEMPT_PREFIX = "email:otp-attempts:";
    private static final String VERIFIED_PREFIX = "email:verified:";

    static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int SEND_LIMIT_PER_EMAIL = 5;
    private static final int SEND_LIMIT_PER_IP = 20;
    private static final int VERIFY_LIMIT_PER_IP = 60;
    private static final Duration HOUR = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    public void sendOtp(String rawEmail, String clientIp) {
        String email = normalize(rawEmail);
        rateLimiter.consume("email-send:ip:" + clientIp, SEND_LIMIT_PER_IP, HOUR,
                "인증번호 요청이 너무 많습니다. 1시간 후 다시 시도해 주세요.");
        rateLimiter.cooldown("email-send-cooldown:" + email, RESEND_COOLDOWN,
                "인증번호는 1분에 한 번만 요청할 수 있어요. 잠시 후 다시 시도해 주세요.");
        rateLimiter.consume("email-send:email:" + email, SEND_LIMIT_PER_EMAIL, HOUR,
                "이 이메일로 인증번호를 너무 많이 요청했어요. 1시간 후 다시 시도해 주세요.");

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        // 새 인증번호를 보냈으니 틀린 횟수는 처음부터 다시 센다.
        redisTemplate.delete(ATTEMPT_PREFIX + email);
        try {
            sendMail(email, otp);
        } catch (ResponseStatusException e) {
            // 발송 자체가 실패했으면 바로 다시 요청할 수 있게 대기시간을 풀어 준다.
            rateLimiter.reset("email-send-cooldown:" + email);
            throw e;
        }
    }

    public String verifyOtp(String rawEmail, String code, String clientIp) {
        String email = normalize(rawEmail);
        rateLimiter.consume("email-verify:ip:" + clientIp, VERIFY_LIMIT_PER_IP, HOUR,
                "인증 시도가 너무 많습니다. 1시간 후 다시 시도해 주세요.");

        String stored = redisTemplate.opsForValue().get(OTP_PREFIX + email);
        if (stored == null) {
            throw badRequest("인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!stored.equals(code == null ? null : code.trim())) {
            Long attempts = redisTemplate.opsForValue().increment(ATTEMPT_PREFIX + email);
            redisTemplate.expire(ATTEMPT_PREFIX + email, Duration.ofSeconds(OTP_TTL_SECONDS));
            if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                // 6자리 번호를 계속 대입하지 못하도록 인증번호 자체를 폐기한다.
                redisTemplate.delete(OTP_PREFIX + email);
                redisTemplate.delete(ATTEMPT_PREFIX + email);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "인증번호를 " + MAX_VERIFY_ATTEMPTS + "번 틀렸어요. 인증번호를 다시 요청해 주세요.");
            }
            long left = MAX_VERIFY_ATTEMPTS - (attempts == null ? 0 : attempts);
            throw badRequest("인증번호가 올바르지 않습니다. (남은 시도 " + left + "회)");
        }

        redisTemplate.delete(OTP_PREFIX + email);
        redisTemplate.delete(ATTEMPT_PREFIX + email);

        String verificationToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFIED_PREFIX + verificationToken, email, VERIFIED_TTL_SECONDS, TimeUnit.SECONDS);
        return verificationToken;
    }

    public String consumeVerifiedEmail(String verificationToken) {
        String email = redisTemplate.opsForValue().get(VERIFIED_PREFIX + verificationToken);
        if (email == null) {
            throw badRequest("이메일 인증이 만료되었거나 유효하지 않습니다.");
        }
        redisTemplate.delete(VERIFIED_PREFIX + verificationToken);
        return email;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
            log.error("인증 메일 발송 실패: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
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
