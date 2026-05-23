package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.AdminAuditLogSummary;
import com.b2b.instantneed.admin.entity.AdminAuditLog;
import com.b2b.instantneed.admin.repository.AdminAuditLogRepository;
import com.b2b.instantneed.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Read-only view into the admin audit log.
 *
 * GET /api/v1/admin/audit-logs
 *   ?entityType=PRODUCT|CATEGORY|CUSTOMER|ORDER  (optional filter)
 *   &actionType=CREATE|UPDATE|DELETE              (optional, requires entityType)
 *   &adminUserId=<uuid>                           (optional filter by who performed the action)
 *   &page=1&limit=50
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogRepository auditLogRepository;

    @GetMapping
    public PagedResponse<AdminAuditLogSummary> listAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) UUID adminUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {

        int safePage  = Math.max(1, page) - 1;
        int safeLimit = Math.min(Math.max(1, limit), 200);

        PageRequest pageable = PageRequest.of(safePage, safeLimit, Sort.by("createdAt").descending());

        Page<AdminAuditLog> results;

        if (adminUserId != null) {
            results = auditLogRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId, pageable);
        } else if (entityType != null && actionType != null) {
            results = auditLogRepository.findByEntityTypeAndActionTypeOrderByCreatedAtDesc(
                    entityType.toUpperCase(), actionType.toUpperCase(), pageable);
        } else if (entityType != null) {
            results = auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(
                    entityType.toUpperCase(), pageable);
        } else {
            results = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return PagedResponse.of(results.map(AdminAuditLogSummary::from));
    }
}
