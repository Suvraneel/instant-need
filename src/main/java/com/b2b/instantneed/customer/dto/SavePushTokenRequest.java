package com.b2b.instantneed.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record SavePushTokenRequest(
        @NotBlank String token
) {}
