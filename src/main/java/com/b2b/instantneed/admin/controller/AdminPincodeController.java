package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.PincodeMinOrderRequest;
import com.b2b.instantneed.admin.dto.PincodeMinOrderResponse;
import com.b2b.instantneed.admin.service.AdminPincodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Pincode Rules", description = "Pincode minimum order management (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/pincode-rules")
@RequiredArgsConstructor
public class AdminPincodeController {

    private final AdminPincodeService service;

    @Operation(summary = "List all pincode minimum order rules")
    @GetMapping
    public ResponseEntity<List<PincodeMinOrderResponse>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @Operation(summary = "Create a pincode minimum order rule")
    @PostMapping
    public ResponseEntity<PincodeMinOrderResponse> create(@Valid @RequestBody PincodeMinOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "Update a pincode minimum order rule")
    @PutMapping("/{id}")
    public ResponseEntity<PincodeMinOrderResponse> update(
            @PathVariable UUID id, @Valid @RequestBody PincodeMinOrderRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "Delete a pincode minimum order rule")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
