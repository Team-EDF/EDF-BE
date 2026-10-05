package com.edf.teamedf.common.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    /** access/refresh 중 더 긴 수명만큼 탈퇴 표시를 유지한다. */
    @Value("#{T(java.lang.Math).max(${jwt.access-exp}, ${jwt.refresh-exp})}")
    private long withdrawnTtlSeconds;

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

    // ==================== 탈퇴 계정 차단 ====================

    private String withdrawnKey(String uuid) {
        return "withdrawn:" + uuid;
    }

    /**
     * 탈퇴한 계정 표시. access token 은 서버에 저장하지 않아 회수할 수 없으므로,
     * 가장 긴 토큰 수명(refresh 만료 시간) 동안 이 표시가 있으면 인증을 거부한다.
     */
    public void markWithdrawn(String uuid) {
        redisTemplate.opsForValue().set(withdrawnKey(uuid), "1", withdrawnTtlSeconds, TimeUnit.SECONDS);
    }

    /** Redis 장애로 조회하지 못하면 모든 요청이 막히지 않도록 false 로 본다. */
    public boolean isWithdrawn(String uuid) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(withdrawnKey(uuid)));
        } catch (RuntimeException e) {
            log.warn("탈퇴 계정 여부 조회 실패 (uuid={})", uuid, e);
            return false;
        }
    }
}
