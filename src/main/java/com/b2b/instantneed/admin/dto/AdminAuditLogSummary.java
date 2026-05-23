package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.admin.entity.AdminAuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogSummary(
        UUID id,
        UUID adminUserId,
        String adminEmail,
        String actionType,
        String entityType,
        String entityId,
        String description,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant createdAt
) {
    public static AdminAuditLogSummary from(AdminAuditLog log) {
        return new AdminAuditLogSummary(
                log.getId(),
                log.getAdminUser().getId(),
                log.getAdminUser().getEmail(),
                log.getActionType(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDescription(),
                log.getBeforeJson(),
                log.getAfterJson(),
                log.getCreatedAt()
        );
    }
}
