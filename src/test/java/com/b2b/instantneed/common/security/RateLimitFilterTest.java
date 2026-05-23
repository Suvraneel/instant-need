package com.b2b.instantneed.common.security;

import com.b2b.instantneed.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper());
    }

    @Test
    void unratedPath_alwaysPasses() throws Exception {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest  req  = request("POST", "/api/v1/cart", "1.2.3.4");
            MockHttpServletResponse res  = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertThat(res.getStatus()).isEqualTo(200); // default — not rate-limited
        }
        verify(filterChain, times(20)).doFilter(any(), any());
    }

    @Test
    void loginEndpoint_allowsUpToFiveRequests_thenRejects() throws Exception {
        String ip = "10.0.0.1";
        // First 5 should pass
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest  req = request("POST", "/api/v1/auth/login", ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertThat(res.getStatus())
                    .as("Request %d should pass", i + 1)
                    .isEqualTo(200);
        }
        // 6th should be rejected
        MockHttpServletRequest  req6 = request("POST", "/api/v1/auth/login", ip);
        MockHttpServletResponse res6 = new MockHttpServletResponse();
        filter.doFilterInternal(req6, res6, filterChain);
        assertThat(res6.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(filterChain, times(5)).doFilter(any(), any()); // chain only called 5 times
    }

    @Test
    void differentIps_getBucketsSeparately() throws Exception {
        // Exhaust IP A
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(
                    request("POST", "/api/v1/auth/login", "192.168.1.1"),
                    new MockHttpServletResponse(), filterChain);
        }
        // IP B should still have its own fresh bucket
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilterInternal(
                request("POST", "/api/v1/auth/login", "192.168.1.2"),
                resB, filterChain);
        assertThat(resB.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedFor_headerUsedAsClientIp() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/v1/auth/login", "proxy-addr");
        req.addHeader("X-Forwarded-For", "203.0.113.5, proxy");
        // Exhaust the bucket for the real IP
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest r = request("POST", "/api/v1/auth/login", "ignored");
            r.addHeader("X-Forwarded-For", "203.0.113.5, proxy");
            filter.doFilterInternal(r, new MockHttpServletResponse(), filterChain);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, filterChain);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void registerEndpoint_allowsThreeRequests_thenRejects() throws Exception {
        String ip = "10.0.0.2";
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(
                    request("POST", "/api/v1/auth/register", ip), res, filterChain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse res4 = new MockHttpServletResponse();
        filter.doFilterInternal(
                request("POST", "/api/v1/auth/register", ip), res4, filterChain);
        assertThat(res4.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static MockHttpServletRequest request(String method, String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
