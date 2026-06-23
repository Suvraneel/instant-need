package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.entity.PincodeMinOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PincodeMinOrderResponse(
        UUID id,
        String pincode,
        BigDecimal minAmount,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PincodeMinOrderResponse from(PincodeMinOrder entity) {
        return new PincodeMinOrderResponse(
                entity.getId(),
                entity.getPincode(),
                entity.getMinAmount(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
