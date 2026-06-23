package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PincodeMinOrderRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Pincode must be exactly 6 digits")
        String pincode,

        @NotNull @DecimalMin(value = "0.01", message = "Minimum order amount must be greater than 0")
        BigDecimal minAmount,

        Boolean active
) {}
