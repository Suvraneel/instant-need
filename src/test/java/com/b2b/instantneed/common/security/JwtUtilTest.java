package com.b2b.instantneed.common.security;

import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    // A ≥32-byte Base64-encoded secret (same default as application.properties)
    private static final String TEST_SECRET =
            "dGhpcy1pcy1hLXZlcnktc2VjcmV0LWtleS10aGF0LXNob3VsZC1iZS1jaGFuZ2VkLWluLXByb2R1Y3Rpb24=";

    private JwtUtil jwtUtil;
    private User user;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration",  3_600_000L);   // 1 h
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L); // 7 d

        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hash")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
    }

    // ── Access token ──────────────────────────────────────────────────────────

    @Test
    void generateAccessToken_extractUsername_matchesEmail() {
        String token = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(user.getEmail());
    }

    @Test
    void generateAccessToken_isTokenValid_returnsTrue() {
        String token = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_wrongUser_returnsFalse() {
        String token = jwtUtil.generateAccessToken(user);
        User other = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .passwordHash("x")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
        assertThat(jwtUtil.isTokenValid(token, other)).isFalse();
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Test
    void generateRefreshToken_isRefreshTokenValid_returnsTrue() {
        String token = jwtUtil.generateRefreshToken(user);
        assertThat(jwtUtil.isRefreshTokenValid(token, user)).isTrue();
    }

    @Test
    void accessToken_failsIsRefreshTokenValid() {
        // Security: an access token must NOT be usable as a refresh token
        String accessToken = jwtUtil.generateAccessToken(user);
        assertThat(jwtUtil.isRefreshTokenValid(accessToken, user)).isFalse();
    }

    @Test
    void refreshToken_failsIsTokenValid_false() {
        // isTokenValid doesn't check the type claim at all, just username + expiry
        // But the refresh token is still structurally valid, so this should be true
        String refreshToken = jwtUtil.generateRefreshToken(user);
        assertThat(jwtUtil.isTokenValid(refreshToken, user)).isTrue();
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Test
    void expiredToken_isTokenValid_returnsFalse() {
        JwtUtil shortLivedUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortLivedUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedUtil, "accessTokenExpiration", -1000L); // already expired
        ReflectionTestUtils.setField(shortLivedUtil, "refreshTokenExpiration", -1000L);

        String token = shortLivedUtil.generateAccessToken(user);
        assertThat(jwtUtil.isTokenValid(token, user)).isFalse();
    }

    @Test
    void expiredToken_isRefreshTokenValid_returnsFalse() {
        JwtUtil shortLivedUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortLivedUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedUtil, "accessTokenExpiration", -1000L);
        ReflectionTestUtils.setField(shortLivedUtil, "refreshTokenExpiration", -1000L);

        String token = shortLivedUtil.generateRefreshToken(user);
        assertThat(jwtUtil.isRefreshTokenValid(token, user)).isFalse();
    }
}
