package com.b2b.instantneed.auth.dto;

import com.b2b.instantneed.user.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(UUID id, String fullName, String email, Role role) {}
}
