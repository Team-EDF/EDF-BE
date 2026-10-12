package com.edf.teamedf.common.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Redis 고정 윈도(fixed window) 카운터 기반 요청 제한.
 *
 * <p>인증번호 발송·확인, 로그인, 주소 검증처럼 로그인 없이 부를 수 있고 비용이 들거나
 * 무차별 대입 대상이 되는 API 를 막는 용도다. 키마다 window 동안 limit 번까지 허용한다.</p>
 *
 * <p>Redis 장애로 카운트를 못 하면 서비스 전체가 막히지 않도록 허용(fail-open)하고 경고만 남긴다.
 * (인증번호 자체도 Redis 에 저장되므로 Redis 가 죽으면 해당 기능은 어차피 동작하지 않는다)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String PREFIX = "rl:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 호출 1회를 기록하고, window 안에서 limit 을 넘으면 429 를 던진다.
     *
     * @param key     제한 단위 (예: "email-send:ip:1.2.3.4")
     * @param limit   window 동안 허용 횟수
     * @param window  집계 구간
     * @param message 제한에 걸렸을 때 사용자에게 보여 줄 문구
     */
    public void consume(String key, int limit, Duration window, String message) {
        long count = increment(key, window);
        if (count > limit) {
            throw tooManyRequests(message);
        }
    }

    /** 기록하지 않고 이미 limit 이상 쌓였는지만 본다 (로그인 실패 횟수처럼 실패할 때만 세는 경우). */
    public void check(String key, int limit, String message) {
        if (current(key) >= limit) {
            throw tooManyRequests(message);
        }
    }

    /** 실패 1회를 기록한다. 첫 기록 시점부터 window 가 지나면 초기화된다. */
    public long increment(String key, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(PREFIX + key);
            if (count != null && count == 1L) {
                redisTemplate.expire(PREFIX + key, window);
            }
            return count == null ? 0L : count;
        } catch (RuntimeException e) {
            log.warn("요청 제한 카운트 실패, 허용 처리 (key={}): {}", key, e.toString());
            return 0L;
        }
    }

    public long current(String key) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("요청 제한 조회 실패, 허용 처리 (key={}): {}", key, e.toString());
            return 0L;
        }
    }

    public void reset(String key) {
        try {
            redisTemplate.delete(PREFIX + key);
        } catch (RuntimeException e) {
            log.warn("요청 제한 초기화 실패 (key={}): {}", key, e.toString());
        }
    }

    /**
     * window 동안 한 번만 허용한다 (재발송 대기시간용). 이미 잡혀 있으면 429.
     */
    public void cooldown(String key, Duration window, String message) {
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, "1", window);
        } catch (RuntimeException e) {
            log.warn("재요청 대기시간 확인 실패, 허용 처리 (key={}): {}", key, e.toString());
            return;
        }
        if (Boolean.FALSE.equals(acquired)) {
            throw tooManyRequests(message);
        }
    }

    private static ResponseStatusException tooManyRequests(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
