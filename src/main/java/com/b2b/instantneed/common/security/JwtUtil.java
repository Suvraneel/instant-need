package com.b2b.instantneed.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(userDetails, accessTokenExpiration, "access");
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, refreshTokenExpiration, "refresh");
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Returns true only if the token is a valid refresh token (type claim == "refresh")
     * and belongs to the given user. Prevents access tokens from being used as refresh tokens.
     */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return username.equals(userDetails.getUsername())
                    && "refresh".equals(type)
                    && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /** Extract the unique token ID (jti) — used for blacklisting on logout. */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /** How long until this token expires — used to set Redis TTL so the key auto-cleans. */
    public Duration extractRemainingTtl(String token) {
        Date expiry = extractClaim(token, Claims::getExpiration);
        long millis = Math.max(0, expiry.getTime() - System.currentTimeMillis());
        return Duration.ofMillis(millis);
    }

    private String buildToken(UserDetails userDetails, long expiration, String type) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti — unique per token
                .subject(userDetails.getUsername())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey())
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(
                Jwts.parser()
                        .verifyWith(signingKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
        );
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
