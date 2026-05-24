package com.b2b.instantneed.auth.controller;

import com.b2b.instantneed.auth.dto.*;
import com.b2b.instantneed.auth.service.AuthService;
import com.b2b.instantneed.common.config.SecurityConfig;
import com.b2b.instantneed.common.security.JwtAuthFilter;
import com.b2b.instantneed.common.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.test.autoconfigure.webmvc.SecurityMockMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.b2b.instantneed.user.entity.Role;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                SecurityMockMvcAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}
        )
)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;

    // ── POST /register ────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returns201() throws Exception {
        given(authService.register(any()))
                .willReturn(new AuthResponse(
                        "access-token", "refresh-token",
                        new AuthResponse.UserInfo(UUID.randomUUID(), "Raj Sharma", "buyer@test.com", Role.CUSTOMER)));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void register_missingFullName_returns400() throws Exception {
        String body = """
                {
                  "email": "test@example.com",
                  "password": "Password1!",
                  "address": {
                    "line1": "12 MG Road",
                    "city": "Mumbai",
                    "state": "Maharashtra",
                    "country": "India",
                    "postalCode": "400001"
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withoutAddress_stillReturns201() throws Exception {
        // Address is optional — registration without one must succeed
        given(authService.register(any()))
                .willReturn(new AuthResponse(
                        "access-token", "refresh-token",
                        new AuthResponse.UserInfo(UUID.randomUUID(), "Test", "test@example.com", Role.CUSTOMER)));

        String body = """
                { "fullName": "Test", "email": "test@example.com", "password": "Password1!" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ── POST /login ───────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        given(authService.login(any()))
                .willReturn(new AuthResponse(
                        "access-token", "refresh-token",
                        new AuthResponse.UserInfo(UUID.randomUUID(), "Raj Sharma", "buyer@test.com", Role.CUSTOMER)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "buyer@test.com", "password": "Buyer@123" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("buyer@test.com"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "", "password": "Buyer@123" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200() throws Exception {
        given(authService.refresh(any()))
                .willReturn(new AuthResponse(
                        "new-access", "new-refresh",
                        new AuthResponse.UserInfo(UUID.randomUUID(), "Raj", "buyer@test.com", Role.CUSTOMER)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "some-valid-token" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void refresh_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /forgot-password ─────────────────────────────────────────────────

    @Test
    void forgotPassword_alwaysReturns200() throws Exception {
        willDoNothing().given(authService).forgotPassword(any());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "anyone@example.com" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String validRegisterBody() {
        return """
                {
                  "fullName": "Raj Sharma",
                  "businessName": "Sharma Traders",
                  "email": "buyer@test.com",
                  "password": "Buyer@123",
                  "address": {
                    "line1": "12 MG Road",
                    "city": "Mumbai",
                    "state": "Maharashtra",
                    "country": "India",
                    "postalCode": "400001"
                  }
                }
                """;
    }
}
