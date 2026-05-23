package com.b2b.instantneed.auth.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        UUID customerId,
        String message
) {}
