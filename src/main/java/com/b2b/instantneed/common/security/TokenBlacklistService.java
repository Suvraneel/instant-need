package com.b2b.instantneed.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps a Redis set of revoked JWT IDs (jti claims).
 * Each key has a TTL equal to the token's remaining lifetime so Redis
 * auto-cleans entries once they would have expired anyway.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "bl:";   // "blacklist:"

    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean redisUnavailableLogged = new AtomicBoolean(false);

    /** Revoke a token by its jti; the entry expires automatically after `ttl`. */
    public void revoke(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", ttl);
            clearRedisUnavailableFlagIfRecovered();
        } catch (DataAccessException ex) {
            logRedisUnavailable(ex);
        }
    }

    /** Returns true if this jti has been revoked (is in the blacklist). */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            boolean revoked = Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
            clearRedisUnavailableFlagIfRecovered();
            return revoked;
        } catch (DataAccessException ex) {
            logRedisUnavailable(ex);
            // Fail open so auth checks don't 500 when Redis is down.
            return false;
        }
    }

    private void logRedisUnavailable(DataAccessException ex) {
        if (redisUnavailableLogged.compareAndSet(false, true)) {
            log.warn("Redis unavailable for token blacklist; continuing without revocation checks until Redis recovers.", ex);
        }
    }

    private void clearRedisUnavailableFlagIfRecovered() {
        if (redisUnavailableLogged.compareAndSet(true, false)) {
            log.info("Redis connection restored; token blacklist checks resumed.");
        }
    }
}
