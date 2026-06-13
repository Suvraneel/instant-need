package com.b2b.instantneed.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Keeps a Redis set of revoked JWT IDs (jti claims).
 * Each key has a TTL equal to the token's remaining lifetime so Redis
 * auto-cleans entries once they would have expired anyway.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "bl:";   // "blacklist:"

    private final StringRedisTemplate redisTemplate;

    /** Revoke a token by its jti; the entry expires automatically after `ttl`. */
    public void revoke(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
    }

    /** Returns true if this jti has been revoked (is in the blacklist). */
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
