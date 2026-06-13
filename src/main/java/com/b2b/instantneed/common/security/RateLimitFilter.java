package com.b2b.instantneed.common.security;

import com.b2b.instantneed.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting applied before Spring Security processing.
 *
 * Limits:
 *   /api/v1/auth/login            — 5  requests / minute  per IP  (brute-force guard)
 *   /api/v1/auth/register         — 3  requests / 10 min  per IP  (signup spam guard)
 *   /api/v1/auth/forgot-password  — 3  requests / 10 min  per IP  (enumeration guard)
 *   /api/v1/products              — 100 requests / minute per IP  (scraping guard)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // Keyed by "bucketType:clientIp"
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // OPTIONS preflights must reach Spring Security's CORS filter so that
        // Access-Control-* headers are written before the browser checks them.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String ip   = extractIp(request);

        BucketType type = resolveBucketType(path);

        if (type != null) {
            Bucket bucket = buckets.computeIfAbsent(type.name() + ":" + ip,
                    k -> Bucket.builder().addLimit(type.bandwidth()).build());

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded: type={} ip={} path={}", type, ip, path);
                writeTooManyRequests(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of("RATE_LIMIT_EXCEEDED",
                        "Too many requests. Please slow down and try again."));
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].strip();
        return request.getRemoteAddr();
    }

    private BucketType resolveBucketType(String path) {
        if (path.equals("/api/v1/auth/login"))           return BucketType.AUTH_LOGIN;
        if (path.equals("/api/v1/auth/register"))        return BucketType.AUTH_REGISTER;
        if (path.equals("/api/v1/auth/forgot-password")) return BucketType.AUTH_FORGOT;
        if (path.startsWith("/api/v1/products"))         return BucketType.SEARCH;
        return null; // no limiting on other paths
    }

    private enum BucketType {
        /** 5 requests per 60 seconds — login brute-force guard */
        AUTH_LOGIN(Bandwidth.builder()
                .capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build()),

        /** 3 requests per 10 minutes — signup spam guard */
        AUTH_REGISTER(Bandwidth.builder()
                .capacity(3).refillGreedy(3, Duration.ofMinutes(10)).build()),

        /** 3 requests per 10 minutes — email enumeration guard */
        AUTH_FORGOT(Bandwidth.builder()
                .capacity(3).refillGreedy(3, Duration.ofMinutes(10)).build()),

        /** 100 requests per 60 seconds — catalog scraping guard */
        SEARCH(Bandwidth.builder()
                .capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build());

        private final Bandwidth bandwidth;
        BucketType(Bandwidth bandwidth) { this.bandwidth = bandwidth; }
        Bandwidth bandwidth() { return bandwidth; }
    }
}
