package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.AdminCustomerSummary;
import com.b2b.instantneed.admin.dto.UpdateCustomerRoleRequest;
import com.b2b.instantneed.admin.dto.UpdateCustomerStatusRequest;
import com.b2b.instantneed.admin.service.AdminCustomerService;
import com.b2b.instantneed.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin — Customers", description = "Customer account management (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService service;

    @Operation(summary = "List all customers")
    @GetMapping
    public ResponseEntity<PagedResponse<AdminCustomerSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.listCustomers(search, page, limit));
    }

    @Operation(summary = "Get a customer by ID")
    @GetMapping("/{customerId}")
    public ResponseEntity<AdminCustomerSummary> get(@PathVariable UUID customerId) {
        return ResponseEntity.ok(service.getCustomer(customerId));
    }

    @Operation(summary = "Activate or deactivate a customer account")
    @PatchMapping("/{customerId}/status")
    public ResponseEntity<AdminCustomerSummary> updateStatus(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(customerId, request));
    }

    @Operation(summary = "Promote or demote a customer between CUSTOMER and ADMIN roles",
               description = "Allowed values: CUSTOMER, ADMIN. SUPER_ADMIN cannot be granted via API.")
    @PatchMapping("/{customerId}/role")
    public ResponseEntity<AdminCustomerSummary> updateRole(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRoleRequest request) {
        return ResponseEntity.ok(service.updateRole(customerId, request));
    }
}
