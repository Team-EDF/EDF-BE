package com.edf.teamedf.common.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    private String refreshKey(String uuid) {
        return "refresh:" + uuid;
    }

    // 저장
    public void save(String uuid, String refreshToken, long ttlSeconds) {
        String key = refreshKey(uuid);
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
        log.info("Redis 저장 완료 → {}, token={}", key, refreshToken);
    }

    // 조회
    public String get(String uuid) {
        return redisTemplate.opsForValue().get(refreshKey(uuid));
    }

    // 삭제
    public void delete(String uuid) {
        redisTemplate.delete(refreshKey(uuid));
    }
}
