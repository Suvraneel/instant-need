package com.b2b.instantneed.admin.repository;

import com.b2b.instantneed.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    Page<AdminAuditLog> findByEntityTypeAndActionTypeOrderByCreatedAtDesc(
            String entityType, String actionType, Pageable pageable);

    Page<AdminAuditLog> findByAdminUserIdOrderByCreatedAtDesc(UUID adminUserId, Pageable pageable);

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
