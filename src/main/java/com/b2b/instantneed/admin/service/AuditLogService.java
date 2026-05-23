package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.entity.AdminAuditLog;
import com.b2b.instantneed.admin.repository.AdminAuditLogRepository;
import com.b2b.instantneed.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    public static final String PRODUCT  = "PRODUCT";
    public static final String CATEGORY = "CATEGORY";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ORDER    = "ORDER";

    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Record an admin action.
     *
     * @param actionType  CREATE | UPDATE | DELETE
     * @param entityType  PRODUCT | CATEGORY | CUSTOMER | ORDER
     * @param entityId    UUID of the affected entity
     * @param description Human-readable summary e.g. "Updated availability to OUT_OF_STOCK"
     * @param before      State before the action (null for creates)
     * @param after       State after the action (null for deletes)
     */
    public void log(String actionType, String entityType, UUID entityId,
                    String description, Object before, Object after) {
        try {
            User admin = currentAdmin();
            AdminAuditLog entry = AdminAuditLog.builder()
                    .adminUser(admin)
                    .actionType(actionType)
                    .entityType(entityType)
                    .entityId(entityId.toString())
                    .description(description)
                    .beforeJson(toMap(before))
                    .afterJson(toMap(after))
                    .build();
            repository.save(entry);
        } catch (Exception e) {
            // Audit log must never break the main operation
            log.error("Failed to write audit log: action={} entity={}/{} error={}",
                    actionType, entityType, entityId, e.getMessage());
        }
    }

    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    private User currentAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user;
        throw new IllegalStateException("No authenticated admin in security context");
    }
}
